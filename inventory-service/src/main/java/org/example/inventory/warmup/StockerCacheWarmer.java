package org.example.inventory.warmup;

import jakarta.annotation.Resource;
import org.example.inventory.common.RedisKeyConstants;
import org.example.inventory.entity.Stock;
import org.example.inventory.mapper.StockMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

public class StockerCacheWarmer implements ApplicationRunner {
    public static final String STOCKER_KEY_PREFIX = RedisKeyConstants.STOCKER_KEY_PREFIX;

    @Resource
    private StockMapper stockMapper;

    @Resource
    StringRedisTemplate stringRedisTemplate;


    private static final Logger log= LoggerFactory.getLogger(StockerCacheWarmer.class);
    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("======  开始库存预热  ======");
        List<Stock> stockList= stockMapper.selectList(null);
        if (stockList.isEmpty() || stockList==null) {
            log.warn("数据库中并无数据,预热结束");
            return;
        }

        for (Stock stock : stockList) {
            String key = STOCKER_KEY_PREFIX + stock.getTicketItemId();
            stringRedisTemplate.opsForValue().set(key, stock.getQuantity().toString());
            log.info("库存预热:Key={},Value={}", key, stock.getQuantity().toString());
        }
        log.info("======  库存预热加载完成  ======");

    }
}
