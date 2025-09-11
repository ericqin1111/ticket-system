package org.example.order.service.Impl;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.order.DTO.OrderCreationMessage;
import org.example.order.entity.Order;

import org.example.order.DTO.CreateOrderRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.example.order.entity.Outbox;
import org.example.order.feign.InventoryFeignClient;
import org.example.order.mapper.OrderMapper;
import org.example.order.mapper.OutboxMapper;
import org.example.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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

    @Autowired
    OutboxMapper outboxMapper;

    @Resource
    RedisTemplate redisTemplate;

    private final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    @Override
    public String createOrderRequest(Long userId,CreateOrderRequest request) {

        String lockKey = "lock:order:create:userId:" + userId + ":ticketItemId:" + request.getTicketItemId();
        Boolean isLocked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);

        if (Boolean.FALSE.equals(isLocked)) {
            log.warn("重复的下单请求被拒绝, UserId: {}, TicketItemId: {}", userId, request.getTicketItemId());
            return null; // 或者 throw new DuplicateRequestException("请勿重复提交");
        }

        boolean deductStatus = inventoryFeignClient.preDeductStock(request.getTicketItemId(), request.getQuantity());

        if (!deductStatus) {
            log.warn("预扣减失败，下单被拒绝 UserId: {},TicketItemId: {}", request.getUserId(), request.getTicketItemId());
            return null;
        }

        try{



            String orderSerialNo = UUID.randomUUID().toString().replace("-", "");



            OrderCreationMessage message = new OrderCreationMessage(
                    userId, // <-- 关键改动：设置用户ID
                    request.getTicketItemId(),
                    request.getQuantity(),
                    orderSerialNo
            );

            createOrderInDB(message);

            Outbox outboxEvent = new Outbox();
            outboxEvent.setId(UUID.randomUUID().toString());
            outboxEvent.setAggregateType("Order");
            outboxEvent.setAggregateId(orderSerialNo);
            outboxEvent.setEventType("OrderCreated");
            outboxEvent.setPayload(objectMapper.writeValueAsString(message)); // 序列化为 JSON

            outboxMapper.insert(outboxEvent);

            return orderSerialNo;
        }
        catch (Exception e){
            log.error("订单序列化失败",e);
            return null;
        }
    }

    public void createOrderInDB(OrderCreationMessage request){
        Order order=new Order();

        order.setOrderSn(request.getOrderSerialNo());
        order.setUserId(request.getUserId());
        BigDecimal price=new  BigDecimal(50);
        order.setTotalAmount(price.multiply(new BigDecimal(request.getQuantity())));
        order.setStatus(0);
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());

        orderMapper.insert(order);
        log.info("订单创建成功,ID:{}",order.getOrderSn());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void processOrderCreation(OrderCreationMessage request) {
        try {
            // 1. 实际扣减数据库库存 (Feign 调用)
            // 这一步也需要幂等，或者支持重复调用无副作用
            boolean dbDeductStatus = inventoryFeignClient.deductStockInDB(request.getTicketItemId(), request.getQuantity());

            if (dbDeductStatus) {
                log.info("数据库库存扣减成功，开始创建订单 TicketItemId: {}", request.getTicketItemId());

                int updatedRows = orderMapper.updateStatus(request.getOrderSerialNo(), 1);
                if (updatedRows == 0) {
                    log.warn("Order not found or status already updated for OrderSN: {}", request.getOrderSerialNo());
                    // 可能是一个重复消息，或者订单不存在，需要有相应的处理
                }

            } else {
                log.warn("数据库扣减失败，需要回补缓存  TicketItemId: {}", request.getTicketItemId());
                inventoryFeignClient.rollbackStockInCache(request.getTicketItemId(), request.getQuantity());
                // 考虑是否需要抛出异常以进行重试
            }



            log.info("Order {} confirmed.", request.getOrderSerialNo());
        } catch (DuplicateKeyException e) {
            // 捕获唯一键冲突异常
            // 这不是一个错误！这是幂等性保证机制成功拦截了重复消息！
            log.warn("幂等性检查：订单已存在，忽略重复消息. OrderSN: {}", request.getOrderSerialNo());
            // 什么都不做，方法正常结束，Kafka 会认为消息已成功消费
        }
        // 其他所有 Exception 会因为 @Transactional 注解而导致事务回滚，
        // 并且会冒泡到 KafkaListener，触发重试。
    }
}
