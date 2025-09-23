package org.example.order.service.Impl;

import Constant.RedisKeyConstants;
import org.example.order.service.QueueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class QueueServiceImpl implements QueueService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;



    @Override
    public Map<String, Object> enterQueue(Long userId, Long ticketItemId) {
        String queueKey=String.format(RedisKeyConstants.QUEUE_TICKET,ticketItemId);
        String userStr = String.valueOf(userId);

        stringRedisTemplate.opsForZSet().addIfAbsent(queueKey,userStr,System.nanoTime());
        return checkStatus(userId,ticketItemId);
    }

    @Override
    public Map<String, Object> checkStatus(Long userId, Long ticketItemId) {
        Map<String,Object> status = new HashMap<>();
        String userStr = String.valueOf(userId);

        String passKey = String.format(RedisKeyConstants.QUEUE_PASS,ticketItemId,userStr);
        String passToken= stringRedisTemplate.opsForValue().get(passKey);

        if(passToken!=null){
            status.put("status","ALLOWED");
            status.put("passToken",passToken);
            return status;
        }

        String queueKey=String.format(RedisKeyConstants.QUEUE_TICKET,ticketItemId);
        Long rank=stringRedisTemplate.opsForZSet().rank(queueKey,userStr);

        if(rank!=null){
            status.put("status","QUEUED");
            status.put("position",rank+1);
            Long totalInQueue=stringRedisTemplate.opsForZSet().size(queueKey);
            status.put("total",totalInQueue);
        }
        else{
            status.put("status","NOT_IN_QUEUE");
        }
        return status;
    }
}
