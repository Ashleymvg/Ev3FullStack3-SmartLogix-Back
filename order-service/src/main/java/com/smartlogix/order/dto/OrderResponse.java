package com.smartlogix.order.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import com.smartlogix.order.domain.OrderStatus;

public record OrderResponse(
        String orderNumber,
        OrderStatus status,
        BigDecimal totalAmount,
        String trackingCode,
        String reason,
        OffsetDateTime createdAt,
        String shippingAddress,
        List<OrderLineResponse> lines,

        // Boleta: datos del cliente y subtotal antes de descuentos
        String customerName,
        String customerEmail,
        BigDecimal subtotal,

        // LogixPoints: campos añadidos 
        int pointsRedeemed,   // puntos que se descontaron en esta orden
        int pointsEarned      // puntos que ganó el cliente con esta compra
) {
}
