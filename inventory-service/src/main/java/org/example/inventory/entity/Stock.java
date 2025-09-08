package org.example.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.math.BigInteger;

@Data
@TableName("stock")
public class Stock {
    BigInteger id;
    BigDecimal ticketItemId;
    Integer quantity;
}
