package lk.jiat.globaltrade.ejb.service;

import jakarta.ejb.Local;
import lk.jiat.globaltrade.common.exception.InsufficientInventoryException;
import lk.jiat.globaltrade.domain.InventoryItem;

import java.util.List;

@Local
public interface InventoryService {
    void reserve(String sku, String warehouse, int quantity) throws InsufficientInventoryException;
    void release(String sku, String warehouse, int quantity);
    List<InventoryItem> findBelowReorderPoint();
    int replenishAllBelowReorderPoint();
}
