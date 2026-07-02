package com.smartlogix.order.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Cliente HTTP del order-service hacia el auth-service
 * para gestionar los LogixPoints del programa de fidelización.
 */
@Component
public class PointsClient {

    private static final Logger log = LoggerFactory.getLogger(PointsClient.class);
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final RestTemplate restTemplate;
    private final String authServiceUrl;
    private final String internalSecret;

    public PointsClient(
            RestTemplate restTemplate,
            @Value("${smartlogix.auth-service.url:http://auth-service}") String authServiceUrl,
            @Value("${smartlogix.internal-secret}") String internalSecret) {
        this.restTemplate = restTemplate;
        this.authServiceUrl = authServiceUrl;
        this.internalSecret = internalSecret;
    }

    public int redeemPoints(String username, int points, String bearerToken) {
        try {
            log.info("[PointsClient] Canjeando {} puntos para '{}'", points, username);
            HttpHeaders headers = buildHeaders(bearerToken);
            Map<String, Object> body = Map.of("username", username, "points", points);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    authServiceUrl + "/api/auth/points/redeem",
                    entity,
                    Map.class
            );

            if (response != null && response.containsKey("pointsRedeemed")) {
                int redeemed = ((Number) response.get("pointsRedeemed")).intValue();
                log.info("[PointsClient] Puntos canjeados exitosamente: {}", redeemed);
                return redeemed;
            }
        } catch (Exception e) {
            log.error("[PointsClient] Error al canjear puntos para '{}': {}", username, e.getMessage(), e);
        }
        return 0;
    }

    public int earnPoints(String username, double totalAmount, String bearerToken) {
        try {
            log.info("[PointsClient] Sumando puntos para '{}' por totalAmount={}", username, totalAmount);
            HttpHeaders headers = buildHeaders(bearerToken);
            Map<String, Object> body = Map.of("username", username, "totalAmount", totalAmount);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            restTemplate.postForObject(
                    authServiceUrl + "/api/auth/points/earn",
                    entity,
                    Map.class
            );

            int earned = (int) Math.floor(totalAmount * 0.01);
            log.info("[PointsClient] Puntos ganados: {}", earned);
            return earned;
        } catch (Exception e) {
            log.error("[PointsClient] Error al sumar puntos para '{}': {}", username, e.getMessage(), e);
        }
        return 0;
    }

    private HttpHeaders buildHeaders(String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearerToken != null && !bearerToken.isBlank()) {
            headers.set(HttpHeaders.AUTHORIZATION, bearerToken);
        }
        // Prueba de que la llamada viene del propio backend (order-service),
        // y no de un cliente externo golpeando /earn o /redeem directamente.
        headers.set(INTERNAL_TOKEN_HEADER, internalSecret);
        return headers;
    }
}
