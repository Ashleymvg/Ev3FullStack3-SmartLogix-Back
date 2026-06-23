package com.smartlogix.auth.dto;

/**
 * DTO que devuelve el saldo actual de LogixPoints de un usuario.
 */
public record PointsResponse(
        String username,
        int logixPoints
) {}
