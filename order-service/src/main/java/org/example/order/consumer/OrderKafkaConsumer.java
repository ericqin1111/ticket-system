package org.example.order.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.example.order.DTO.CreateOrderRequest;
import org.example.order.DTO.OrderCreationMessage;
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

//    @KafkaListener( topics = "order_topics",groupId = "order_consumer_group")
    @KafkaListener(topics = "mysql_server_ticket.ticket_system.outbox", groupId = "order_consumer_group")
    public void handleOrderCreation(String message){
        log.info("接收到创建订单的请求: {}",message);

        try {
//            OrderCreationMessage request = objectMapper.readValue(message, OrderCreationMessage.class);
//
//            // 调用包含核心业务逻辑的 service 方法
//            orderService.processOrderCreation(request);
//            JsonNode rootNode = objectMapper.readTree(message);
//            // 提取 payload 字段
//            JsonNode payloadNode = rootNode.path("payload");
//
//            // 将 payload 节点转换为你的 DTO
//            OrderCreationMessage request = objectMapper.treeToValue(payloadNode, OrderCreationMessage.class);
//
//            // 调用包含核心业务逻辑的 service 方法
//            orderService.processOrderCreation(request);
            JsonNode rootNode = objectMapper.readTree(message);

            // 取出 after 部分（即 Outbox 表的一行数据）
            JsonNode afterNode = rootNode.path("payload");

            // 拿到 payload（注意：是字符串）
            String payloadStr = afterNode.asText();

            // 反序列化成 DTO
            OrderCreationMessage request = objectMapper.readValue(payloadStr, OrderCreationMessage.class);

            // 执行业务逻辑
            orderService.processOrderCreation(request);


        } catch (JsonProcessingException e) {
            log.error("反序列化订单消息失败 {}", message, e);
            // 这种错误通常无法重试，可以直接签收
        } catch (Exception e) {
            log.error("处理订单消息时发生未知错误，将等待重试 {}", message, e);
            // 抛出异常，触发 Kafka 的重试机制
            throw new RuntimeException(e);
        }


    }


}
