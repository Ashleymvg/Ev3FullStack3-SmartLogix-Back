package com.smartlogix.order.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public record CreateOrderRequest(
        @NotBlank String customerName,
        @NotBlank @Email String customerEmail,
        @NotBlank String shippingAddress,
        @NotEmpty List<@Valid OrderLineRequest> lines,

        // LogixPoints: campos añadidos para el programa de fidelización
        boolean usePoints,      // true si el cliente quiere usar sus puntos
        int pointsToUse         // cantidad de puntos que desea canjear
) {
}
