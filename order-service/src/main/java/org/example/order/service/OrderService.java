package org.example.order.service;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.service.IService;
import org.example.order.DTO.CreateOrderRequest;

public interface OrderService{
    public boolean createOrderRequest(CreateOrderRequest request);

    public void createOrderInDB(CreateOrderRequest request);
}
