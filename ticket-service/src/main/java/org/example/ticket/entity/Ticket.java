package org.example.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tickets")
public class Ticket {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
