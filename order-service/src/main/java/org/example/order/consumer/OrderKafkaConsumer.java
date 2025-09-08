package org.example.order.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.example.order.DTO.CreateOrderRequest;
import org.example.order.feign.InventoryFeignClient;
import org.example.order.service.Impl.OrderServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;


@Component
public class OrderKafkaConsumer {
    @Resource
    private InventoryFeignClient inventoryFeignClient;

    @Resource
    private OrderServiceImpl orderService;

    Logger log = LoggerFactory.getLogger(OrderKafkaConsumer.class);
    @Autowired
    private ObjectMapper objectMapper;

    @KafkaListener( topics = "order_topics",groupId = "order_consumer_group")
    public void handleOrderCreation(String message){
        log.info("接收到创建订单的请求: {}",message);

        try {
            CreateOrderRequest request=objectMapper.readValue(message,CreateOrderRequest.class);
            boolean dbDeductStatus = inventoryFeignClient.deductStockInDB(request.getTicketItemId(),request.getQuantity());
            if(dbDeductStatus){
                log.info("开始创建订单 TicketItemId: {}",request.getTicketItemId());
                orderService.createOrderInDB(request);
            }
            else{
                log.warn("数据库扣减失败，需要回补缓存  TicketItemId: {}",request.getTicketItemId());
                inventoryFeignClient.rollbackStockInCache(request.getTicketItemId(), request.getQuantity());
            }

        }catch (JsonProcessingException e){
            log.error("反序列化订单消息失败 {}",message,e);
        }
        catch (Exception e){
            log.error("处理订单消息时发生未知错误 {}",message,e);
        }
    }


}
