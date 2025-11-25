package org.example.ticket.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ticket.cache.key.PriceTierCacheKeyBuilder;
import org.example.ticket.cache.core.CacheTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class TicketUpdateConsumer {

    private final CacheTemplate cacheTemplate;
    private final PriceTierCacheKeyBuilder priceTierCacheKeyBuilder;

    @KafkaListener(topics = "ticket_update_topic",groupId = "ticket_update_group")
    public void handleTicketUpdate(String priceTierIdStr){
        try{
            log.info("从Kafka收到清楚priceTierId:{}的缓存",priceTierIdStr);
            Long priceTierId = Long.valueOf(priceTierIdStr);
            cacheTemplate.invalidate(priceTierCacheKeyBuilder.build(priceTierId));
        }
        catch (NumberFormatException e){
            log.error("Tier Id{}不是一个有效值",priceTierIdStr,e);
        }
        catch (Exception e){
            log.error("在更新priceTier:{}的过程中失败",priceTierIdStr,e);
        }
    }

}
