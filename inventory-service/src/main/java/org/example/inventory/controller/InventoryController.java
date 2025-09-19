package org.example.inventory.controller;

import org.example.inventory.service.Impl.InventoryServiceImpl;
import org.example.inventory.service.InventoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InventoryController {
    @Autowired
    private InventoryServiceImpl inventoryService;

    @PostMapping("/inventory/pre-deduct")
    boolean preDeductStockInCache(@RequestParam("ticketItemId")Long ticketItemId,@RequestParam("quantity")Integer quantity){
        return inventoryService.preDeductStockInCache(ticketItemId,quantity);
    }

    @PostMapping("/inventory/deduct-db")
    boolean deductStockInDB(@RequestParam("orderSn") String orderSn,@RequestParam("ticketItemId")Long ticketItemId,@RequestParam("quantity")Integer quantity){
        return inventoryService.deductStockInDB(orderSn,ticketItemId,quantity);
    }

    @PostMapping("/inventory/rollback-cache")
    void rollbackStockInCache(@RequestParam("ticketItemId")Long ticketItemId,@RequestParam("quantity")Integer quantity)
    {
        inventoryService.rollbackStockInCache(ticketItemId,quantity);
    }
}
