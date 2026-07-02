package com.smartlogix.auth.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartlogix.auth.domain.UserEntity;
import com.smartlogix.auth.dto.AdjustPointsRequest;
import com.smartlogix.auth.dto.PointsResponse;
import com.smartlogix.auth.dto.UserPointsSummary;
import com.smartlogix.auth.exception.AuthException;
import com.smartlogix.auth.repository.UserRepository;

/**
 * Servicio de LogixPoints — gestiona la acumulación y descuento
 * de puntos de fidelización (estilo CMR).
 *
 * Regla de acumulación: 1% del totalAmount en puntos (1 punto = $1 CLP).
 * Regla de canje: 1 punto descuenta $1 del total de la compra.
 */
@Service
@Transactional
public class PointsService {

    /** 1 punto = $1. El cliente acumula 1% del total en puntos. */
    public static final double EARN_RATE = 0.01;

    private final UserRepository userRepository;

    public PointsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Obtiene el saldo de puntos de un usuario por username.
     */
    @Transactional(readOnly = true)
    public PointsResponse getPoints(String username) {
        UserEntity user = findUser(username);
        return new PointsResponse(user.getUsername(), user.getLogixPoints());
    }

    /**
     * Suma puntos ganados tras una compra exitosa.
     * El order-service llama este endpoint internamente.
     *
     * @param username   email o username del cliente
     * @param totalAmount monto total de la compra (ya descontado si usó puntos)
     * @return saldo nuevo de puntos
     */
    public PointsResponse earnPoints(String username, double totalAmount) {
        UserEntity user = findByUsernameOrEmail(username);
        int earned = (int) Math.floor(totalAmount * EARN_RATE);
        if (earned > 0) {
            user.setLogixPoints(user.getLogixPoints() + earned);
            userRepository.save(user);
        }
        return new PointsResponse(user.getUsername(), user.getLogixPoints());
    }

    /**
     * Descuenta puntos cuando el cliente decide usarlos al hacer checkout.
     * Devuelve la cantidad de puntos efectivamente descontados.
     *
     * @param username      email o username del cliente
     * @param pointsToUse   puntos que el cliente quiere usar
     * @return puntos descontados (puede ser menor si no tiene suficiente saldo)
     */
    public int redeemPoints(String username, int pointsToUse) {
        UserEntity user = findByUsernameOrEmail(username);
        int available = user.getLogixPoints();
        int toRedeem = Math.min(pointsToUse, available);
        if (toRedeem > 0) {
            user.setLogixPoints(available - toRedeem);
            userRepository.save(user);
        }
        return toRedeem;
    }

    /**
     * Ajuste manual de puntos — solo ROLE_ADMIN.
     * Operaciones: ADD | SUBTRACT | SET
     */
    public PointsResponse adjustPoints(AdjustPointsRequest req) {
        UserEntity user = findUser(req.username());
        int current = user.getLogixPoints();
        int points = req.points();

        switch (req.operation().toUpperCase()) {
            case "ADD"      -> user.setLogixPoints(current + points);
            case "SUBTRACT" -> user.setLogixPoints(current - points);
            case "SET"      -> user.setLogixPoints(points);
            default -> throw new AuthException("Operación inválida. Use ADD, SUBTRACT o SET.");
        }

        userRepository.save(user);
        return new PointsResponse(user.getUsername(), user.getLogixPoints());
    }

    /**
     * Lista todos los usuarios con su saldo de puntos — solo ROLE_ADMIN.
     */
    @Transactional(readOnly = true)
    public List<UserPointsSummary> getAllUsersPoints() {
        return userRepository.findAll().stream()
                .map(u -> new UserPointsSummary(
                        u.getId(),
                        u.getUsername(),
                        u.getEmail(),
                        u.getRole().name(),
                        u.getLogixPoints()
                ))
                .toList();
    }

    // <─ helpers ─>

    private UserEntity findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new AuthException("Usuario no encontrado: " + username));
    }

    private UserEntity findByUsernameOrEmail(String credential) {
        return userRepository.findByUsername(credential)
                .or(() -> userRepository.findByEmail(credential))
                .orElseThrow(() -> new AuthException("Usuario no encontrado: " + credential));
    }
}
