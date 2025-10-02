package org.example.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("price_tiers")
public class PriceTier {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long eventId;
    private String tierName;
    private BigDecimal price;
    private Integer totalInventory;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
