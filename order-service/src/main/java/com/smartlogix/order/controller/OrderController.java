package com.smartlogix.order.controller;

import com.smartlogix.order.dto.CreateOrderRequest;
import com.smartlogix.order.dto.OrderResponse;
import com.smartlogix.order.dto.SyncTrackingRequest;
import com.smartlogix.order.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public OrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.createOrder(request);
    }

    @GetMapping
    public List<OrderResponse> listOrders() {
        return orderService.getOrders();
    }

    @GetMapping("/{orderNumber}")
    public OrderResponse findByOrderNumber(@PathVariable String orderNumber) {
        return orderService.getOrderByNumber(orderNumber);
    }

    // Sincroniza el pedido cuando se le asigna un envío manualmente desde
    // el panel de Envíos (Admin/Bodega), ya que ese flujo no pasa por el
    // proceso automático que actualiza el pedido durante su creación.
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE_MANAGER')")
    @PatchMapping("/{orderNumber}/sync-tracking")
    public OrderResponse syncTracking(
            @PathVariable String orderNumber,
            @Valid @RequestBody SyncTrackingRequest request) {
        return orderService.syncTracking(orderNumber, request.trackingCode());
    }
}
