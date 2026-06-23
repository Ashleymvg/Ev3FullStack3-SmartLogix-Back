package com.smartlogix.order.service;

import com.smartlogix.order.client.InventoryAvailabilityResponse;
import com.smartlogix.order.client.InventoryClient;
import com.smartlogix.order.client.InventoryClientException;
import com.smartlogix.order.client.PointsClient;
import com.smartlogix.order.client.ShipmentClient;
import com.smartlogix.order.client.ShipmentRequest;
import com.smartlogix.order.client.ShipmentResponse;
import com.smartlogix.order.domain.OrderLine;
import com.smartlogix.order.domain.OrderStatus;
import com.smartlogix.order.domain.PurchaseOrder;
import com.smartlogix.order.dto.CreateOrderRequest;
import com.smartlogix.order.dto.OrderLineRequest;
import com.smartlogix.order.dto.OrderLineResponse;
import com.smartlogix.order.dto.OrderResponse;
import com.smartlogix.order.exception.OrderNotFoundException;
import com.smartlogix.order.repository.PurchaseOrderRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@Transactional
public class OrderService {

    private final PurchaseOrderRepository repository;
    private final InventoryClient inventoryClient;
    private final ShipmentClient shipmentClient;
    private final PointsClient pointsClient;

    public OrderService(
            PurchaseOrderRepository repository,
            InventoryClient inventoryClient,
            ShipmentClient shipmentClient,
            PointsClient pointsClient
    ) {
        this.repository = repository;
        this.inventoryClient = inventoryClient;
        this.shipmentClient = shipmentClient;
        this.pointsClient = pointsClient;
    }

    public OrderResponse createOrder(CreateOrderRequest request) {
        PurchaseOrder order = buildOrder(request);
        repository.save(order);

        for (OrderLine line : order.getLines()) {
            InventoryAvailabilityResponse availability = inventoryClient.checkAvailability(line.getSku(), line.getQuantity());
            if (availability == null || !availability.available()) {
                order.setStatus(OrderStatus.REJECTED);
                order.setRejectionReason("Stock insuficiente para SKU " + line.getSku());
                repository.save(order);
                return toResponse(order, 0, 0);
            }
        }

        List<OrderLine> reservedLines = new ArrayList<>();
        for (OrderLine line : order.getLines()) {
            try {
                inventoryClient.reserve(line.getSku(), line.getQuantity());
                reservedLines.add(line);
            } catch (InventoryClientException ex) {
                releaseReservedLines(reservedLines);
                order.setStatus(OrderStatus.REJECTED);
                order.setRejectionReason("No fue posible reservar inventario. " + ex.getMessage());
                repository.save(order);
                return toResponse(order, 0, 0);
            }
        }

        // ── LogixPoints: descuento de puntos si el cliente lo solicitó ──────
        int pointsRedeemed = 0;
        if (request.usePoints() && request.pointsToUse() > 0) {
            String bearerToken = extractBearerToken();
            String customerEmail = request.customerEmail();
            pointsRedeemed = pointsClient.redeemPoints(customerEmail, request.pointsToUse(), bearerToken);

            if (pointsRedeemed > 0) {
                // 1 punto = $1 de descuento
                BigDecimal discount = BigDecimal.valueOf(pointsRedeemed);
                BigDecimal newTotal = order.getTotalAmount().subtract(discount);
                if (newTotal.compareTo(BigDecimal.ZERO) < 0) newTotal = BigDecimal.ZERO;
                order.setTotalAmount(newTotal);
                repository.save(order);
            }
        }

        order.setStatus(OrderStatus.APPROVED);

        ShipmentResponse shipmentResponse = shipmentClient.requestShipment(
                new ShipmentRequest(order.getOrderNumber(), order.getShippingAddress(), totalUnits(order))
        );

        if (shipmentResponse == null || shipmentResponse.trackingCode() == null) {
            order.setStatus(OrderStatus.FAILED);
            order.setRejectionReason("Servicio de envios no disponible. Asignacion manual requerida.");
            repository.save(order);

            // ── LogixPoints: sumar puntos igual — inventario ya fue reservado ──
            int pointsEarnedOnFail = 0;
            try {
                String bearerToken = extractBearerToken();
                pointsEarnedOnFail = pointsClient.earnPoints(
                        request.customerEmail(),
                        order.getTotalAmount().doubleValue(),
                        bearerToken
                );
            } catch (Exception ignored) {}

            return toResponse(order, pointsRedeemed, pointsEarnedOnFail);
        }

        order.setStatus(OrderStatus.SHIPMENT_REQUESTED);
        order.setTrackingCode(shipmentResponse.trackingCode());
        repository.save(order);

        // ── LogixPoints: acumular puntos tras compra exitosa ─────────────────
        int pointsEarned = 0;
        try {
            String bearerToken = extractBearerToken();
            pointsEarned = pointsClient.earnPoints(
                    request.customerEmail(),
                    order.getTotalAmount().doubleValue(),
                    bearerToken
            );
        } catch (Exception ignored) {
            // La acumulación de puntos es un proceso no crítico; la orden ya se completó
        }

        return toResponse(order, pointsRedeemed, pointsEarned);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrders() {
        return repository.findAll().stream()
                .map(o -> toResponse(o, 0, 0))
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderByNumber(String orderNumber) {
        PurchaseOrder order = repository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new OrderNotFoundException("No existe la orden " + orderNumber));
        return toResponse(order, 0, 0);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private PurchaseOrder buildOrder(CreateOrderRequest request) {
        PurchaseOrder order = new PurchaseOrder();
        order.setCustomerName(request.customerName().trim());
        order.setCustomerEmail(request.customerEmail().trim().toLowerCase());
        order.setShippingAddress(request.shippingAddress().trim());
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(calculateTotal(request.lines()));

        for (OrderLineRequest lineRequest : request.lines()) {
            OrderLine line = new OrderLine();
            line.setSku(lineRequest.sku().trim().toUpperCase());
            line.setQuantity(lineRequest.quantity());
            line.setUnitPrice(lineRequest.unitPrice());
            order.addLine(line);
        }

        return order;
    }

    private BigDecimal calculateTotal(List<OrderLineRequest> lines) {
        return lines.stream()
                .map(line -> line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private int totalUnits(PurchaseOrder order) {
        return order.getLines().stream().mapToInt(OrderLine::getQuantity).sum();
    }

    private void releaseReservedLines(List<OrderLine> reservedLines) {
        for (OrderLine line : reservedLines) {
            try {
                inventoryClient.release(line.getSku(), line.getQuantity());
            } catch (Exception ignored) {
            }
        }
    }

    private OrderResponse toResponse(PurchaseOrder order, int pointsRedeemed, int pointsEarned) {
        List<OrderLineResponse> lines = order.getLines().stream()
                .map(line -> new OrderLineResponse(
                        line.getSku(),
                        line.getQuantity(),
                        line.getUnitPrice(),
                        line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity()))
                ))
                .toList();

        return new OrderResponse(
                order.getOrderNumber(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getTrackingCode(),
                order.getRejectionReason(),
                order.getCreatedAt(),
                order.getShippingAddress(),
                lines,
                pointsRedeemed,
                pointsEarned
        );
    }

    /**
     * Extrae el Bearer token de la request HTTP actual para
     * reenviarlo al auth-service al gestionar puntos.
     */
    private String extractBearerToken() {
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
                HttpServletRequest req = attrs.getRequest();
                String header = req.getHeader(HttpHeaders.AUTHORIZATION);
                if (header != null && header.startsWith("Bearer ")) {
                    return header;
                }
            }
        } catch (Exception ignored) {}
        return "";
    }
}
