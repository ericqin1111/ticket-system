package org.example.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@TableName("outbox")
@Data
public class Outbox {
    private String id;
    private String aggregateType;
    private String aggregateId;
    private String eventType;

    private String payload;
}
