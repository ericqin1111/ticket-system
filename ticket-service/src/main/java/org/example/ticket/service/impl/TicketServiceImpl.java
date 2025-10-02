package org.example.ticket.service.impl;

import Constant.RedisKeyConstants;
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

    private final Logger log;
    private final ObjectMapper objectMapper;

    @Autowired
    public TicketServiceImpl(TicketMapper ticketMapper, StringRedisTemplate stringRedisTemplate, EventMapper eventMapper, PriceTierMapper priceTierMapper, KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.ticketMapper = ticketMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.eventMapper = eventMapper;
        this.priceTierMapper = priceTierMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.log = LoggerFactory.getLogger(TicketServiceImpl.class);
        this.objectMapper = objectMapper;
    }

    private static final String CACHE_NAME_PRICE_TIER = "priceTier";

    private static final String KAFKA_TICKET_UPDATE_TOPIC = "ticket_update_topic";

    @Override
    @Cacheable(value = CACHE_NAME_PRICE_TIER,key = "#priceTierId")
    public PriceTierDetailDTO getPriceTierDetails(Long priceTierId) {
        log.info("[L1缓存]:L1中无{}的记录",priceTierId);

        String redisKey = String.format(RedisKeyConstants.REDIS_PRICE_TIER_KEY,priceTierId);
        String json = stringRedisTemplate.opsForValue().get(redisKey);

        if(StringUtils.hasText(json)){
            log.info("[L2 CACHE]存在键 key:{}",redisKey);
            try{
                return objectMapper.readValue(json,PriceTierDetailDTO.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("在序列化Redis priceTier失败",e);
            }
        }

        log.warn("[L1 CACHE] [L2 CACHE]均无记录,准备从数据库中查询priceTierId:{}",priceTierId);
        PriceTier priceTier=priceTierMapper.selectById(priceTierId);
        if(priceTier==null){
            log.warn("数据库中未能查询到priceTier Id:{}",priceTierId);
            return null;
        }

        Event event = eventMapper.selectById(priceTier.getEventId());
        Ticket ticket = (event != null)? ticketMapper.selectById(event.getTicketId()):null;

        PriceTierDetailDTO dto = buildPriceTierDetailDTO(priceTier,event,ticket);

        try{
            String dtoJson = objectMapper.writeValueAsString(dto);
            long expireTime = 60L + (long)(Math.random()*30);
            stringRedisTemplate.opsForValue().set(redisKey,dtoJson,expireTime, TimeUnit.MINUTES);
            log.info("[L2 CACHE] 写入key:{}",redisKey);
        } catch (JsonProcessingException e) {
            log.error("序列化priceTierDto失败",e);
        }

        return dto;
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

        log.info("Tier{}数据更新成功，向kafka发送清空缓存的消息",dto.getTierId());
        kafkaTemplate.send(KAFKA_TICKET_UPDATE_TOPIC,String.valueOf(dto.getTierId()));
    }

    @Override
    @CacheEvict(value = CACHE_NAME_PRICE_TIER,key = "#priceTierId")
    public void evictPriceTierCacher(Long priceTierId) {
        String redisKey = String.format(RedisKeyConstants.REDIS_PRICE_TIER_KEY,priceTierId);
        Boolean deleted = stringRedisTemplate.delete(redisKey);
        log.info("[CACHE EVICTION] 从L1缓存中删除记录 key:{},L2缓存中删除 key:{}, success:{}",priceTierId,redisKey,deleted);

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
                // Ticket info
                dto.setTicketName(ticket.getTitle());
                dto.setDescription(ticket.getDescription());
            }
        }
        return dto;
    }
}
