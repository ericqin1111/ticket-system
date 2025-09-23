package org.example.order.controller;

import Constant.RedisKeyConstants;
import jakarta.annotation.Resource;
import org.example.order.DTO.QueueConfigRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/queue")
public class QueueAdminController {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @PostMapping("/config")
    public ResponseEntity<?> updateConfig(@RequestBody QueueConfigRequest queueConfigRequest){
        String configKey= String.format(RedisKeyConstants.QUEUE_CONFIG,queueConfigRequest.getTicketItemId());

        String activeTicketKey=String.format(RedisKeyConstants.QUEUE_ACTIVE_TICKETS);
        Map<String,String> config=new HashMap<>();

        if(queueConfigRequest.getReleaseRate()!=null){
            config.put("release_rate",String.valueOf(queueConfigRequest.getReleaseRate()));
        }
        if(queueConfigRequest.getIsActive()!=null && queueConfigRequest.getIsActive()){
            config.put("is_active","1");
            stringRedisTemplate.opsForSet().add(activeTicketKey, String.valueOf(queueConfigRequest.getTicketItemId()));
        }
        else{
            config.put("is_active","0");
            stringRedisTemplate.opsForSet().remove(String.valueOf(queueConfigRequest.getTicketItemId()));
        }

        stringRedisTemplate.opsForHash().putAll(configKey,config);
        return ResponseEntity.ok().build();
    }

}
