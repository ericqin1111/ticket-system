package Constant;

public final class RedisKeyConstants {
    RedisKeyConstants() {}

    public static String build(String... part){
        return String.join(":", part);
    }
    /**
     * 命名：{业务}:{表名}:
     */

    /* ================ORDER-SERVICE================

    /**
        * 库存
        * 格式 stock:ticket_item:{ticketItemId}
        * 数据结构 String
        * 用途 记录商品在redis缓存
     */
    public static final String STOCKER_KEY_PREFIX = "stock:ticket_item:";

    /**
        * 排队队列
        * 格式 queue:ticket_item:{ticketItemId}
        * 数据结构 ZSET member=userId, score=入队时间辍
        * 用途  存储进入改票务排队的用户
     */
    public static final String QUEUE_TICKET="queue:ticket_item:%s";


    /**
       * 放行通行证
       * 格式 queue:ticket_item:{ticketItemId}:user:{userId}
       * 数据结构 String 值为token
       * 用途  标记某个用户是否有购票资格
       */
    public static final String QUEUE_PASS="queue:ticket_item:%s:user:%s";



    /**
     *  抢购票集合
     *  数据结构 Set
     *  成员 ticketItemId
     *  用途:存储『当前正在排队的票品ID』
     */

    public static final String QUEUE_ACTIVE_TICKETS="config:queue_active_tickets";

    /**
     * 队列配置 Key
     * 格式: config:ticket_item:{ticketItemId}
     * 数据结构: Hash (存放开始时间、并发阈值、批次大小等配置)
     * 用途: 存储某个票务商品的排队策略和配置信息
     */
    public static final String QUEUE_CONFIG="config:ticket_item:%s";



    /**
     * 用户排队状态 Key
     * 格式: queue_user_status:{ticketItemId}:{userId}
     * 数据结构: String (waiting / passed / failed 等状态)
     * 用途: 存储某个用户在该票务商品排队中的当前状态
     */
    public static final String QUEUE_USER_STATUS="queue_user_status:%s:%s";


    /**
     * 队列统计 Key
     * 格式: status:ticket_item:{ticketItemId}
     * 数据结构: Hash (waitingCount, passedCount, failedCount …)
     * 用途: 统计某个票务商品排队相关的数据指标
     */
    public static final String QUEUE_STATISTIC="status:ticket_item:%s";





    /* ==============TICKET-SERVICE================ */

    public static final String REDIS_PRICE_TIER_KEY="ticket:tier:%s";
}

