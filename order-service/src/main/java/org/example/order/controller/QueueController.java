package org.example.order.controller;

import org.example.order.service.QueueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/queue")
public class QueueController {
    @Autowired
    private QueueService queueService;


    @PostMapping("/enter")
    public ResponseEntity<Map<String,Object>> enterQueue(
            @RequestHeader("X-User-ID") Long userId,
            @RequestHeader("X-Ticket-Item-ID") Long ticketItemId){

        Map<String,Object> queueStatus=queueService.enterQueue(userId,ticketItemId);
        return ResponseEntity.ok(queueStatus);
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String,Object>> getQueueStatus(
            @RequestHeader("X-User-ID") Long userId,
            @RequestHeader("X-Ticket-Item-ID") Long ticketItemId) {
        Map<String,Object> status=queueService.checkStatus(userId,ticketItemId);
        return ResponseEntity.ok(status);
    }

}