package org.example.ticket.messaging;

import lombok.extern.slf4j.Slf4j;
import org.example.ticket.service.TicketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TicketUpdateConsumer {
    private TicketService ticketService;

    @Autowired
    public TicketUpdateConsumer(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @KafkaListener(topics = "ticket_update_topic",groupId = "ticket_update_group")
    public void handleTicketUpdate(String priceTierIdStr){
        try{
            log.info("从Kafka收到清楚priceTierId:{}的缓存",priceTierIdStr);
            Long priceTierId = Long.valueOf(priceTierIdStr);
            ticketService.evictPriceTierCacher(priceTierId);
        }
        catch (NumberFormatException e){
            log.error("Tier Id{}不是一个有效值",priceTierIdStr,e);
        }
        catch (Exception e){
            log.error("在更新priceTier:{}的过程中失败",priceTierIdStr,e);
        }
    }

}
