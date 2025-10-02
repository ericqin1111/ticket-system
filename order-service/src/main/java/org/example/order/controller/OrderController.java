package org.example.order.controller;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.order.DTO.CreateOrderRequest;
import org.example.order.entity.Order;
import org.example.order.mapper.OrderMapper;
import org.example.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
public class OrderController {


    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderMapper orderMapper;

    private final Logger log = LoggerFactory.getLogger(OrderController.class);

    @PostMapping("/create")
    public ResponseEntity<Map<String,String>> createOrder(@RequestHeader("X-User-ID") Long userId,@RequestBody CreateOrderRequest request,@RequestHeader("X-Ticket-Item-Id")Long ticketItemId) {

        log.info("Received order creation request from User ID: {}", userId);

        if(orderService.checkConsistency(ticketItemId,request)){
            String orderSn = orderService.createOrderRequest(userId,request);

            if (orderSn != null) {
                // 返回成功，告知前端已进入排队
                return ResponseEntity.ok(Map.of("message", "Request submitted, processing in queue.","orderSn",orderSn));
            } else {
                // 返回失败，告知前端库存不足
                return ResponseEntity.status(400).body(Map.of("message", "Failed to create order, insufficient stock."));
            }
        }
        else{
            return ResponseEntity.status(400).body(Map.of("message", "请求头与body内容不符合"));
        }
    }

    @GetMapping("/user/{userId}/latest")
    public ResponseEntity<Order> getLatestOrderStatus(@PathVariable Long userId) {
        QueryWrapper<Order> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId).orderByDesc("create_time").last("LIMIT 1");
        Order latestOrder = orderMapper.selectOne(queryWrapper);

        if (latestOrder != null) {
            return ResponseEntity.ok(latestOrder);
        } else {
            // 如果还没有订单，可以返回 404 Not Found
            return ResponseEntity.notFound().build();
        }
    }
}
