package org.example.order.service;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.service.IService;
import org.example.order.DTO.CreateOrderRequest;
import org.example.order.DTO.OrderCreationMessage;

public interface OrderService{
    public String createOrderRequest(Long userId,CreateOrderRequest request);

    public void createOrderInDB(OrderCreationMessage request);

    public void processOrderCreation(OrderCreationMessage request);

    public boolean checkConsistency(Long ticketItemId,CreateOrderRequest request);
}
