package org.example.inventory.service;

import org.springframework.stereotype.Service;

public interface InventoryService {

    /**
     *
     * @param ticketItemId 票务项ID
     * @param quantity     数量
     * @return 是否扣减成功
     */
    boolean preDeductStockInCache(Long ticketItemId,Integer quantity);

    boolean deductStockInDB(Long ticketItemId,Integer quantity);

    void rollbackStockInCache(Long ticketItemId,Integer quantity);
}
