package com.smartlogix.order.dto;

import com.smartlogix.order.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record OrderResponse(
        String orderNumber,
        OrderStatus status,
        BigDecimal totalAmount,
        String trackingCode,
        String reason,
        OffsetDateTime createdAt,
        String shippingAddress,
        List<OrderLineResponse> lines,

        // ── LogixPoints: campos añadidos ──
        int pointsRedeemed,   // puntos que se descontaron en esta orden
        int pointsEarned      // puntos que ganó el cliente con esta compra
) {
}
