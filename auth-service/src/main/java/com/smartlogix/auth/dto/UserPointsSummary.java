package com.smartlogix.auth.dto;

/**
 * Resumen de puntos por usuario — usado en el panel de administración.
 */
public record UserPointsSummary(
        Long id,
        String username,
        String email,
        String role,
        int logixPoints
) {}
