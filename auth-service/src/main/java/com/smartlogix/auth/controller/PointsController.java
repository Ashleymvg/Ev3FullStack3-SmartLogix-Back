package com.smartlogix.auth.controller;

import com.smartlogix.auth.dto.AdjustPointsRequest;
import com.smartlogix.auth.dto.PointsResponse;
import com.smartlogix.auth.dto.UserPointsSummary;
import com.smartlogix.auth.exception.AuthException;
import com.smartlogix.auth.service.PointsService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controlador REST para el Programa de Fidelización LogixPoints.
 *
 * GET  /api/auth/points/me             → saldo del usuario en sesión
 * POST /api/auth/points/earn           → sumar puntos tras compra (order-service)
 * POST /api/auth/points/redeem         → canjear puntos en checkout (order-service)
 * GET  /api/auth/points/admin/all      → listado admin (solo ROLE_ADMIN)
 * POST /api/auth/points/admin/adjust   → ajuste manual admin (solo ROLE_ADMIN)
 */
@RestController
@RequestMapping("/api/auth/points")
public class PointsController {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final PointsService pointsService;
    private final String internalSecret;

    public PointsController(
            PointsService pointsService,
            @Value("${smartlogix.internal-secret}") String internalSecret) {
        this.pointsService = pointsService;
        this.internalSecret = internalSecret;
    }

    /**
     * GET /api/auth/points/me
     * Devuelve el saldo de LogixPoints del usuario autenticado.
     */
    @GetMapping("/me")
    public ResponseEntity<PointsResponse> getMyPoints(Authentication auth) {
        return ResponseEntity.ok(pointsService.getPoints(auth.getName()));
    }

    /**
     * POST /api/auth/points/earn
     * Llamado por el order-service tras una compra exitosa.
     * Body: { "username": "...", "totalAmount": 15000 }
     *
     * Protegido con un secreto interno: solo el order-service conoce el
     * valor de X-Internal-Token, así ningún cliente (Postman, etc.) puede
     * llamar este endpoint directamente para autoasignarse puntos gratis.
     */
    @PostMapping("/earn")
    public ResponseEntity<PointsResponse> earnPoints(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = INTERNAL_TOKEN_HEADER, required = false) String internalToken) {
        checkInternalCall(internalToken);
        String username = (String) body.get("username");
        double totalAmount = ((Number) body.get("totalAmount")).doubleValue();
        return ResponseEntity.ok(pointsService.earnPoints(username, totalAmount));
    }

    /**
     * POST /api/auth/points/redeem
     * Llamado por el order-service al procesar el pago.
     * Body: { "username": "...", "points": 1500 }
     *
     * Protegido de la misma forma que /earn.
     */
    @PostMapping("/redeem")
    public ResponseEntity<Map<String, Object>> redeemPoints(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = INTERNAL_TOKEN_HEADER, required = false) String internalToken) {
        checkInternalCall(internalToken);
        String username = (String) body.get("username");
        int pointsToUse = ((Number) body.get("points")).intValue();
        int redeemed = pointsService.redeemPoints(username, pointsToUse);
        return ResponseEntity.ok(Map.of(
                "username", username,
                "pointsRedeemed", redeemed
        ));
    }

    /**
     * GET /api/auth/points/admin/all  — solo ROLE_ADMIN
     * Lista todos los usuarios con su saldo de puntos.
     */
    @GetMapping("/admin/all")
    public ResponseEntity<List<UserPointsSummary>> getAllUsersPoints(Authentication auth) {
        checkAdmin(auth);
        return ResponseEntity.ok(pointsService.getAllUsersPoints());
    }

    /**
     * POST /api/auth/points/admin/adjust  — solo ROLE_ADMIN
     * Ajusta manualmente los puntos de cualquier usuario.
     */
    @PostMapping("/admin/adjust")
    public ResponseEntity<PointsResponse> adjustPoints(
            @Valid @RequestBody AdjustPointsRequest request,
            Authentication auth) {
        checkAdmin(auth);
        return ResponseEntity.ok(pointsService.adjustPoints(request));
    }

    // ── helper: verifica que el usuario sea ROLE_ADMIN ───────────────────────
    private void checkAdmin(Authentication auth) {
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin) {
            throw new AuthException("Acceso denegado. Se requiere ROLE_ADMIN.");
        }
    }

    // ── helper: verifica que la llamada venga del propio backend ─────────────
    private void checkInternalCall(String internalToken) {
        if (internalToken == null || !internalToken.equals(internalSecret)) {
            throw new AuthException("Acceso denegado. Este endpoint es de uso interno.");
        }
    }
}
