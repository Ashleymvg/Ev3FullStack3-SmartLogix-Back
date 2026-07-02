package com.smartlogix.order.client;

import java.time.OffsetDateTime;

/**
 * Espejo del InventoryItemResponse del inventory-service.
 * Se usa para obtener el precio REAL y autoritativo de un producto
 * (nunca se debe confiar en el precio que envía el cliente en el pedido).
 */
public record InventoryItemResponse(
        String sku,
        String productName,
        String warehouseCode,
        int availableQuantity,
        int reservedQuantity,
        int reorderLevel,
        int price,
        OffsetDateTime updatedAt
) {
}
