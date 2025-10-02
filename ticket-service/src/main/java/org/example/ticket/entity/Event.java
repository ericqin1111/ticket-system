package org.example.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("events")
public class Event {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ticketId;
    private LocalDateTime eventTime;
    private Long venueId;
    private String venueName;
    private String city;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
