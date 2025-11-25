package org.example.ticket.service.impl;

import org.example.ticket.DTO.PriceTierDetailDTO;
import org.example.ticket.cache.config.CacheProperties;
import org.example.ticket.cache.core.CacheTemplate;
import org.example.ticket.cache.key.PriceTierCacheKeyBuilder;
import org.example.ticket.cache.model.CacheOptions;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketServiceImpl implements TicketService {
    private final TicketMapper ticketMapper;

    private final EventMapper eventMapper;

    private final PriceTierMapper priceTierMapper;

    private final String PRICE_TIER_BLOOM_FILTER=BloomFIlterInitializer.PRICE_TIER_BLOOM_FILTER;

    private final Logger log;

    private final RedissonClient redissonClient;

    private final CacheTemplate cacheTemplate;
    private final PriceTierCacheKeyBuilder priceTierCacheKeyBuilder;
    private final CacheProperties cacheProperties;

    @Autowired
    public TicketServiceImpl(
            TicketMapper ticketMapper,
            EventMapper eventMapper,
            PriceTierMapper priceTierMapper,
            RedissonClient redissonClient,
            CacheTemplate cacheTemplate,
            PriceTierCacheKeyBuilder priceTierCacheKeyBuilder,
            CacheProperties cacheProperties
    ) {
        this.ticketMapper = ticketMapper;
        this.eventMapper = eventMapper;
        this.priceTierMapper = priceTierMapper;
        this.redissonClient = redissonClient;
        this.log = LoggerFactory.getLogger(TicketServiceImpl.class);
        this.cacheTemplate = cacheTemplate;
        this.priceTierCacheKeyBuilder = priceTierCacheKeyBuilder;
        this.cacheProperties = cacheProperties;
    }

    @Override
    public PriceTierDetailDTO getPriceTierDetails(Long priceTierId) {
        String cacheKey = priceTierCacheKeyBuilder.build(priceTierId);

        return cacheTemplate.get(cacheKey, CacheOptions.from(cacheProperties), () -> {
            RBloomFilter<Long> bloomFilter = redissonClient.getBloomFilter(PRICE_TIER_BLOOM_FILTER);
            if (!bloomFilter.contains(priceTierId)) {
                log.warn("[BLOOM FILTER] 拒绝tierId为{}的请求", priceTierId);
                return null;
            }
            log.info("[CACHE MISS] 准备从数据库查询priceTierId:{}", priceTierId);

            PriceTier priceTier = priceTierMapper.selectById(priceTierId);
            if (priceTier == null) {
                log.warn("数据库未能查询到priceTier Id:{}", priceTierId);
                return null;
            }

            Event event = eventMapper.selectById(priceTier.getEventId());
            Ticket ticket = (event != null) ? ticketMapper.selectById(event.getTicketId()) : null;

            return buildPriceTierDetailDTO(priceTier, event, ticket);
        }, PriceTierDetailDTO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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

        log.info("Tier{}数据更新成功，开始失效缓存",dto.getTierId());
        cacheTemplate.invalidate(priceTierCacheKeyBuilder.build(dto.getTierId()));
        // 预留: 发送失效事件广播
//        kafkaTemplate.send(KAFKA_TICKET_UPDATE_TOPIC,String.valueOf(dto.getTierId()));
    }

    @Override
    public void evictPriceTierCacher(Long priceTierId) {
        cacheTemplate.invalidate(priceTierCacheKeyBuilder.build(priceTierId));
        log.info("已删除缓存 key:{}",priceTierId);
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
