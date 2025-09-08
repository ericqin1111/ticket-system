package org.example.order.DTO;

import lombok.Data;

@Data
public class CreateOrderRequest {
    Long userId;
    Long ticketItemId;
    Integer quantity;
}
