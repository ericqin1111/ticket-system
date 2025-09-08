package org.example.order.service.Impl;

import org.example.order.entity.Order;

import org.example.order.DTO.CreateOrderRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.example.order.feign.InventoryFeignClient;
import org.example.order.mapper.OrderMapper;
import org.example.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class OrderServiceImpl implements OrderService {
    @Resource
    private InventoryFeignClient inventoryFeignClient;

    @Resource
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    OrderMapper orderMapper;

    @Resource
    ObjectMapper objectMapper;

    private final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    @Override
    public boolean createOrderRequest(CreateOrderRequest request) {
        boolean deductStatus = inventoryFeignClient.preDeductStock(request.getTicketItemId(), request.getQuantity());

        if (!deductStatus) {
            log.warn("预扣减失败，下单被拒绝 UserId: {},TicketItemId: {}", request.getUserId(), request.getTicketItemId());
            return false;
        }

        try{
            String message =  objectMapper.writeValueAsString(request);
            kafkaTemplate.send("ORDER_TOPIC", message);
            log.info("库存预扣款成功,已发送创建消息至kafka,消息内容:{}", message);
            return true;
        }
        catch (Exception e){
            log.error("订单序列化失败",e);
            return false;
        }
    }

    public void createOrderInDB(CreateOrderRequest request){
        Order order=new Order();

        order.setOrderSn(UUID.randomUUID().toString().replace("-",""));
        order.setUserId(request.getUserId());
        BigDecimal price=new  BigDecimal(50);
        order.setTotalPrice(price.multiply(new BigDecimal(request.getQuantity())));
        order.setStatus(1);

        orderMapper.insert(order);
        log.info("订单创建成功,ID:{}",order.getOrderSn());
    }
}
