package com.smartlogix.order.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Usado para sincronizar el pedido con el shipment-service cuando
 * se asigna un envío de forma manual (fuera del flujo automático).
 */
public record SyncTrackingRequest(
        @NotBlank String trackingCode
) {
}
