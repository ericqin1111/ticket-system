package org.example.ticket.util;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ticket.entity.PriceTier;
import org.example.ticket.mapper.PriceTierMapper;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class BloomFIlterInitializer implements CommandLineRunner {

    private RedissonClient redisson;

    private PriceTierMapper priceTierMapper;

    public static final String PRICE_TIER_BLOOM_FILTER = "priceTier:BloomFilter";

    @Autowired
    public BloomFIlterInitializer(PriceTierMapper priceTierMapper, RedissonClient redisson) {
        this.priceTierMapper = priceTierMapper;
        this.redisson = redisson;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("正在为PriceTier初始化布隆过滤器");

        RBloomFilter<Long> bloomFilter= redisson.getBloomFilter(PRICE_TIER_BLOOM_FILTER);

        bloomFilter.tryInit(100000L,0.01);
        List<PriceTier> priceTiers = priceTierMapper.selectList(new QueryWrapper<PriceTier>().select("id"));

        if(priceTiers==null||priceTiers.isEmpty()){
            log.warn("[BLOOM INIT]没有PriceTier的相关数据");
            return;
        }

        for(PriceTier priceTier : priceTiers){
            bloomFilter.add(priceTier.getId());
        }

        log.info("布隆过滤器初始化完毕 已向过滤器:{} 加载tier:{}个",PRICE_TIER_BLOOM_FILTER,priceTiers.size());
    }
}
