package com.smartlogix.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request para ajustar LogixPoints de un usuario.
 * operation: ADD | SUBTRACT | SET
 */
public record AdjustPointsRequest(
        @NotBlank String username,
        @NotNull Integer points,
        @NotBlank String operation
) {}
