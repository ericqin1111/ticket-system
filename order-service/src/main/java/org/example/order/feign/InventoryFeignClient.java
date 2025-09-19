package org.example.order.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient("inventory-service")
public interface InventoryFeignClient {
    @PostMapping("/inventory/pre-deduct")
    boolean preDeductStock(@RequestParam("ticketItemId")Long ticketItemId,@RequestParam("quantity") Integer quantity);

    @PostMapping("/inventory/deduct-db")
    boolean deductStockInDB(@RequestParam("orderSn")String orderSn,@RequestParam("ticketItemId")Long ticketItemId,@RequestParam("quantity")Integer quantity);

    @PostMapping("/inventory/rollback-cache")
    void rollbackStockInCache(@RequestParam("ticketItemId")Long ticketItemId,@RequestParam("quantity")Integer quantity);
}
