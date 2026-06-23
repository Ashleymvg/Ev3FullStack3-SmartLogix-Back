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
 *
 * Llama a:
 *  POST /api/auth/points/redeem  → canjear puntos en checkout
 *  POST /api/auth/points/earn    → sumar puntos tras compra exitosa
 */
@Component
public class PointsClient {

    private static final Logger log = LoggerFactory.getLogger(PointsClient.class);

    private final RestTemplate restTemplate;
    private final String authServiceUrl;

    public PointsClient(
            RestTemplate restTemplate,
            @Value("${smartlogix.auth-service.url:http://auth-service}") String authServiceUrl) {
        this.restTemplate = restTemplate;
        this.authServiceUrl = authServiceUrl;
    }

    /**
     * Llama al auth-service para canjear puntos.
     * Devuelve la cantidad real de puntos descontados.
     *
     * @param username    email o username del cliente
     * @param points      puntos que quiere usar
     * @param bearerToken token JWT del usuario (para autenticar la llamada)
     * @return puntos efectivamente canjeados (0 si falla o no tiene saldo)
     */
    public int redeemPoints(String username, int points, String bearerToken) {
        try {
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
                return ((Number) response.get("pointsRedeemed")).intValue();
            }
        } catch (Exception e) {
            log.warn("No se pudo canjear puntos para {}: {}", username, e.getMessage());
        }
        return 0;
    }

    /**
     * Llama al auth-service para sumar puntos tras una compra exitosa.
     *
     * @param username    email o username del cliente
     * @param totalAmount monto final pagado (ya con descuento de puntos aplicado)
     * @param bearerToken token JWT del usuario
     * @return puntos ganados (0 si falla)
     */
    public int earnPoints(String username, double totalAmount, String bearerToken) {
        try {
            HttpHeaders headers = buildHeaders(bearerToken);
            Map<String, Object> body = Map.of("username", username, "totalAmount", totalAmount);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    authServiceUrl + "/api/auth/points/earn",
                    entity,
                    Map.class
            );

            // Calculamos manualmente los puntos ganados para devolverlos en la respuesta
            return (int) Math.floor(totalAmount * 0.01);
        } catch (Exception e) {
            log.warn("No se pudo acumular puntos para {}: {}", username, e.getMessage());
        }
        return 0;
    }

    private HttpHeaders buildHeaders(String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearerToken != null && !bearerToken.isBlank()) {
            headers.set(HttpHeaders.AUTHORIZATION, bearerToken);
        }
        return headers;
    }
}
