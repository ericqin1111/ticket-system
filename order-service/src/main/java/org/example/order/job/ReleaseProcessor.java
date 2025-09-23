package org.example.order.job;

import Constant.RedisKeyConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
public class ReleaseProcessor {
    private static final Logger logger = LoggerFactory.getLogger(ReleaseProcessor.class);

    private static final long RELEASE_RATE_PER_SECOND= 500L;

    private static final String RELEASE_SCRIPT=
            "local userIds = redis.call('ZRANGE',KEYS[1],0,ARGV[1]-1)\n" +
                    "if #userIds > 0 then\n" +
                    "redis.call('ZREM',KEYS[1],unpack(userIds))\n"+
                    "end\n"+
                    "return userIds";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Scheduled(fixedRate = 1000)
    public void releaseUsers(){

        String activeTicket= RedisKeyConstants.QUEUE_ACTIVE_TICKETS;
        Set<String> activeTicketItemIds = stringRedisTemplate.opsForSet().members(activeTicket);

        if (activeTicketItemIds != null && activeTicketItemIds.isEmpty()) {
            return;
        }

        for(String ticketItemIdStr:activeTicketItemIds){
            long ticketItemId = Long.parseLong(ticketItemIdStr);
            String configKey=String.format(RedisKeyConstants.QUEUE_CONFIG,ticketItemId);

            String releaseRateStr=(String) stringRedisTemplate.opsForHash().get(configKey,"release_rate");

            if(releaseRateStr!=null){
                try{
                    long releaseRate = Long.parseLong(releaseRateStr);
                    if(releaseRate > 0){
                        processQueueForItem(ticketItemId,releaseRate);
                    }
                }
                catch (NumberFormatException e){
                    logger.error("无效放行速率:{},ticketItemId:{}",releaseRateStr,ticketItemIdStr,e);
                }
            }
        }

        //       Set<String> configKeys = stringRedisTemplate.keys(String.format(RedisKeyConstants.QUEUE_CONFIG,"*"));

        //        if(configKeys.isEmpty()){
//            return;
//        }
//        for (String configKey : configKeys) {
//            Map<Object, Object> config = stringRedisTemplate.opsForHash().entries(configKey);
//
//            String isActive=(String) config.get("is_active");
//            String releaseRateStr=(String) config.get("release_rate");
//
//            if("1".equals(isActive) && releaseRateStr!=null){
//                try{
//                    long releaseRate = Long.parseLong(releaseRateStr);
//                    if(releaseRate<=0) continue;
//
//                    long ticketItemId = Long.parseLong(configKey.split(":")[2]);
//
//                    processQueueForItem(ticketItemId,releaseRate);
//                }
//                catch (NumberFormatException e){
//                    logger.error("无效放行速率release_rate{}",releaseRateStr,e);
//                }
//            }
//        }



    }

    private void processQueueForItem(long ticketItemId,long releaseRate){
        String queueKey=String.format(RedisKeyConstants.QUEUE_TICKET,ticketItemId);
//        Set<String> userIdsToRelease = stringRedisTemplate.opsForZSet().range(queueKey,0,releaseRate-1);



        DefaultRedisScript<List> redisScript = new DefaultRedisScript<>(RELEASE_SCRIPT, List.class);

        @SuppressWarnings("unchecked")
        List<String> userIdsToRelease=(List<String>) stringRedisTemplate.execute(redisScript, Collections.singletonList(queueKey), String.valueOf(releaseRate));


        if(userIdsToRelease==null || userIdsToRelease.isEmpty()){
            return;
        }
        logger.info("为票{}发出{}通行证",ticketItemId,userIdsToRelease.size());


        stringRedisTemplate.executePipelined((RedisCallback<Object>) connection->{
            for(String userIdStr:userIdsToRelease){
                String passToken= UUID.randomUUID().toString();
                String passKey=String.format(RedisKeyConstants.QUEUE_PASS,ticketItemId,userIdStr);
                connection.stringCommands().setEx(
                        passKey.getBytes(),
                        300,
                        passToken.getBytes()
                );
            }
            return null;
        });


        //stringRedisTemplate.opsForZSet().remove(queueKey, userIdsToRelease.toArray(new String[0]));
    }

}
