package org.example.inventory.service.Impl;

import Constant.RedisKeyConstants;
import jakarta.annotation.Resource;
import org.example.inventory.entity.StockDeductionLog;
import org.example.inventory.mapper.StockDeductionMapper;
import org.example.inventory.mapper.StockMapper;
import org.example.inventory.service.InventoryService;
import org.example.inventory.warmup.StockerCacheWarmer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class InventoryServiceImpl implements InventoryService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private final Logger log = LoggerFactory.getLogger(this.getClass());
    @Autowired
    private StockMapper stockMapper;

    @Autowired
    private StockDeductionMapper stockDeductionLog;
    @Autowired
    private StockDeductionMapper stockDeductionMapper;

    @Override
    public boolean preDeductStockInCache(Long ticketItemId, Integer quantity) {
        String key = RedisKeyConstants.STOCKER_KEY_PREFIX + ticketItemId;

        Long remainStock=stringRedisTemplate.opsForValue().decrement(key, quantity);
        if(remainStock != null && remainStock > 0) {
            log.info("Redis预扣款成功 TicketItemId: {},扣减数量: {},剩余库存: {}", ticketItemId, quantity, remainStock);
            return true;
        }

        if(remainStock != null) {
            stringRedisTemplate.opsForValue().increment(key, quantity);
            log.warn("Redis库存不足,预扣减失败并回补 TicketItemId: {},尝试扣减: {}", ticketItemId, quantity);
        }
        else{
            log.error("Redis预扣减异常,key: {}不存在", key);
        }
        return false;
    }

    @Override
    public void rollbackStockInCache(Long ticketItemId, Integer quantity) {
        String key = RedisKeyConstants.STOCKER_KEY_PREFIX + ticketItemId;
        stringRedisTemplate.opsForValue().increment(key, quantity);
        log.info("库存缓存已补偿 ticketItemId:{},quantity:{}",ticketItemId,quantity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deductStockInDB(String orderSn,Long ticketItemId, Integer quantity) {

        try{
            StockDeductionLog log = new StockDeductionLog(orderSn, LocalDateTime.now());
            stockDeductionMapper.insert(log);
        }
        catch (DuplicateKeyException e){
            log.warn("幂等性检查:库存扣减重复,orderSn:{}",orderSn);
            return true;
        }


        int affectedRow = stockMapper.deductStockInDB(ticketItemId, quantity);

        if(affectedRow > 0) {
            log.info("数据库扣款成功 ticketItemId: {},quantity: {}", ticketItemId, quantity);
            return true;
        }
        else{
            log.warn("数据库扣款失败 ticketItemId: {},quantity: {}", ticketItemId, quantity);
            return false;
        }
    }
}
