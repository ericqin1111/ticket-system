package org.example.order.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("orders")
public class Order {
    @TableId
    Long id;

    String orderSn;

    Long userId;

    private BigDecimal totalPrice;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;



}
