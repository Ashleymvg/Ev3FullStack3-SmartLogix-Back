package com.smartlogix.order.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class InventoryClient {

    private final RestTemplate restTemplate;

    public InventoryClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public InventoryAvailabilityResponse checkAvailability(String sku, int quantity) {
        return restTemplate.getForObject(
                "http://inventory-service/api/inventory/items/{sku}/availability?quantity={quantity}",
                InventoryAvailabilityResponse.class,
                sku,
                quantity
        );
    }

    /**
     * Obtiene los datos autoritativos del producto (incluyendo el precio real)
     * directamente desde el inventory-service. Nunca se debe confiar en el
     * unitPrice que envía el cliente en el cuerpo de la solicitud del pedido.
     */
    public InventoryItemResponse findBySku(String sku) {
        try {
            return restTemplate.getForObject(
                    "http://inventory-service/api/inventory/items/{sku}",
                    InventoryItemResponse.class,
                    sku
            );
        } catch (RestClientException ex) {
            throw new InventoryClientException("No existe el producto con SKU " + sku, ex);
        }
    }

    public void reserve(String sku, int quantity) {
        try {
            restTemplate.postForObject(
                    "http://inventory-service/api/inventory/items/{sku}/reserve?quantity={quantity}",
                    null,
                    Object.class,
                    sku,
                    quantity
            );
        } catch (RestClientException ex) {
            throw new InventoryClientException("No fue posible reservar stock para " + sku, ex);
        }
    }

    public void release(String sku, int quantity) {
        try {
            restTemplate.postForObject(
                    "http://inventory-service/api/inventory/items/{sku}/release?quantity={quantity}",
                    null,
                    Object.class,
                    sku,
                    quantity
            );
        } catch (RestClientException ex) {
            throw new InventoryClientException("No fue posible liberar stock para " + sku, ex);
        }
    }
}
