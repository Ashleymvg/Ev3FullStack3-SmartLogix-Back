package com.smartlogix.inventory.config;

import com.smartlogix.inventory.domain.InventoryItem;
import com.smartlogix.inventory.repository.InventoryItemRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InventorySeedConfig {

    @Bean
    CommandLineRunner inventorySeeder(InventoryItemRepository repository) {
        return args -> {
            if (repository.count() > 0) {
                return;
            }

            repository.save(buildItem("SKU-1001", "Teclado Mecanico RGB", "WH-SCL-01", 120, 20));
            repository.save(buildItem("SKU-2001", "Mouse Inalambrico", "WH-SCL-01", 200, 30));
            repository.save(buildItem("SKU-3001", "Monitor 24 Pulgadas", "WH-VAP-02", 45, 10));

            repository.save(buildItem("SKU-4001", "Gabinete Gamer RGB", "WH-SCL-01", 30, 5));
            repository.save(buildItem("SKU-5001", "Pasta Termica 3g", "WH-VAP-02", 150, 20));
            repository.save(buildItem("SKU-6001", "Disco Duro SSD 1TB", "WH-SCL-01", 80, 15));
            repository.save(buildItem("SKU-7001", "Memoria RAM 16G DDR4", "WH-VAP-02", 100, 20));
            repository.save(buildItem("SKU-8001", "Fuente de Poder 650W 80+ Bronze", "WH-SCL-01", 40, 10));
            repository.save(buildItem("SKU-9001", "Disco Duro SSD 1TB", "WH-VAP-02", 65, 12));
        };
    }

    private InventoryItem buildItem(String sku, String name, String warehouse, int available, int reorderLevel) {
        InventoryItem item = new InventoryItem();
        item.setSku(sku);
        item.setProductName(name);
        item.setWarehouseCode(warehouse);
        item.setAvailableQuantity(available);
        item.setReservedQuantity(0);
        item.setReorderLevel(reorderLevel);
        return item;
    }
}
