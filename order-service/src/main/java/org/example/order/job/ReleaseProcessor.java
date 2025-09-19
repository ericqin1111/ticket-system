package org.example.order.job;

import Constant.RedisKeyConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class ReleaseProcessor {
    private static final Logger logger = LoggerFactory.getLogger(ReleaseProcessor.class);

    private static final long RELEASE_RATE_PER_SECOND= 500L;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Scheduled(fixedRate = 1000)
    public void releaseUsers(){
        String activeQueueSetKey = RedisKeyConstants.QUEUE_ACTIVE_TICKETS;

        Set<String> activeTicketIds= stringRedisTemplate.opsForSet().members(activeQueueSetKey);

        if( activeTicketIds==null || activeTicketIds.isEmpty()){
            return;
        }

        for(String activeTicketId:activeTicketIds){
            long releaseRate=0L;
            String hashkey=String.format(RedisKeyConstants.QUEUE_CONFIG,activeTicketId);
            Object releaseRateObj =  stringRedisTemplate.opsForHash().get(hashkey,"release_rate");
            if (releaseRateObj != null) {
                 releaseRate=   Long.parseLong((String) releaseRateObj);
            }
            else{
                logger.warn("release_rate:{} is null",activeTicketId);
            }
            String queueKey=String.format(RedisKeyConstants.QUEUE_TICKET,activeTicketId);
            Set<String> userIdsToRelease = stringRedisTemplate.opsForZSet().range(queueKey,0,releaseRate-1);

            if(userIdsToRelease==null || userIdsToRelease.isEmpty()){
                return;
            }

            logger.info("为票{}发出{}通行证",activeTicketId,userIdsToRelease.size());

            for(String userId:userIdsToRelease){
                String passToken= UUID.randomUUID().toString();
                String passKey=String.format(RedisKeyConstants.QUEUE_PASS,activeTicketId,userId);

                stringRedisTemplate.opsForValue().set(passKey,passToken,60, TimeUnit.SECONDS);
            }
            stringRedisTemplate.opsForZSet().remove(queueKey, (Object) userIdsToRelease.toArray(new String[0]));

        }
    }

}
