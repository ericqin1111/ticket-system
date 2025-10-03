package org.example.ticket.service.impl;

import Constant.RedisKeyConstants;
import com.alicp.jetcache.anno.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ticket.DTO.PriceTierDetailDTO;
import org.example.ticket.entity.Event;
import org.example.ticket.entity.PriceTier;
import org.example.ticket.entity.Ticket;
import org.example.ticket.mapper.EventMapper;
import org.example.ticket.mapper.PriceTierMapper;
import org.example.ticket.mapper.TicketMapper;
import org.example.ticket.service.TicketService;
import org.example.ticket.util.BloomFIlterInitializer;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

@Service
public class TicketServiceImpl implements TicketService {
    private final TicketMapper ticketMapper;

    private final StringRedisTemplate stringRedisTemplate;

    private final EventMapper eventMapper;

    private final PriceTierMapper priceTierMapper;

    private final KafkaTemplate<String, String> kafkaTemplate;

    private final String PRICE_TIER_BLOOM_FILTER=BloomFIlterInitializer.PRICE_TIER_BLOOM_FILTER;

    private final Logger log;
    private final ObjectMapper objectMapper;

    private final RedissonClient redissonClient;

    @Autowired
    public TicketServiceImpl(TicketMapper ticketMapper, StringRedisTemplate stringRedisTemplate, EventMapper eventMapper, PriceTierMapper priceTierMapper, KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper, RedissonClient redissonClient) {
        this.ticketMapper = ticketMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.eventMapper = eventMapper;
        this.priceTierMapper = priceTierMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.redissonClient = redissonClient;
        this.log = LoggerFactory.getLogger(TicketServiceImpl.class);
        this.objectMapper = objectMapper;
    }

    private static final String CACHE_NAME_PRICE_TIER = "priceTier";

    private static final String KAFKA_TICKET_UPDATE_TOPIC = "ticket_update_topic";

    @Override
    @Cached(name = "priceTierCache:",
    key = "#priceTierId",
    cacheType = CacheType.BOTH,
    expire = 1 , timeUnit = TimeUnit.HOURS,
    cacheNullValue = true)
    @CachePenetrationProtect(timeout = 1000)
    public PriceTierDetailDTO getPriceTierDetails(Long priceTierId) {

        RBloomFilter<Long> bloomFilter = redissonClient.getBloomFilter(PRICE_TIER_BLOOM_FILTER);
        if(!bloomFilter.contains(priceTierId)){
            log.warn("[BLOOM FILTER] 拒绝tierId为{}的请求",priceTierId);
            return null;
        }
        log.info("[BLOOM FILTER] 放行tierId为{}的请求",priceTierId);

        log.warn("JetCache未命中,准备从数据库查询priceTierId:{}", priceTierId);
        PriceTier priceTier=priceTierMapper.selectById(priceTierId);
        if(priceTier==null){
            log.warn("数据库未能查询到priceTier Id:{}", priceTierId);
            return null;
        }

        Event event = eventMapper.selectById(priceTier.getEventId());
        Ticket ticket=(event!=null)?ticketMapper.selectById(event.getTicketId()):null;

        return buildPriceTierDetailDTO(priceTier,event,ticket);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheUpdate(name = "priceTierCache:",key = "#dto.tierId",value = "#dto")
    public void updatePriceTier(PriceTierDetailDTO dto) {
        if(dto.getTierId()==null){
            log.error("更新失败:tierId 为 null");
            return;
        }

        log.info("更新TierId为:{}的记录",dto.getTierId());

        PriceTier priceTierToUpdate=new PriceTier();
        priceTierToUpdate.setId(dto.getTierId());
        priceTierToUpdate.setPrice(dto.getPrice());
        priceTierToUpdate.setTierName(dto.getTierName());
        priceTierToUpdate.setTotalInventory(dto.getTotalInventory());
        priceTierMapper.updateById(priceTierToUpdate);

        log.info("Tier{}数据更新成功，向kafka发送清空缓存的消息",dto.getTierId());
//        kafkaTemplate.send(KAFKA_TICKET_UPDATE_TOPIC,String.valueOf(dto.getTierId()));
    }

    @Override
    @CacheInvalidate(name = "priceTierCache:" ,key="#priceTierId")
    public void evictPriceTierCacher(Long priceTierId) {

        log.info("Jetcache从缓存中删除 key{}:",priceTierId);
    }

    private PriceTierDetailDTO buildPriceTierDetailDTO(PriceTier priceTier, Event event, Ticket ticket) {
        PriceTierDetailDTO dto = new PriceTierDetailDTO();
        // Tier info
        dto.setTierId(priceTier.getId());
        dto.setTierName(priceTier.getTierName());
        dto.setPrice(priceTier.getPrice());
        dto.setTotalInventory(priceTier.getTotalInventory());

        if (event != null) {
            // Event info
            dto.setEventId(event.getId());
            dto.setEventTime(event.getEventTime());
            dto.setVenueName(event.getVenueName());
            dto.setCity(event.getCity());
            dto.setTicketId(event.getTicketId());
            if (ticket != null) {
                dto.setTicketName(ticket.getTitle());
                dto.setDescription(ticket.getDescription());
            }
        }
        return dto;
    }
}
