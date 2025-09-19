package org.example.order.service;

import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Map;

public interface QueueService {
    public Map<String,Object> enterQueue(Long userId,Long ticketItemId);

    public Map<String,Object> checkStatus(Long userId,Long ticketItemId);
}
