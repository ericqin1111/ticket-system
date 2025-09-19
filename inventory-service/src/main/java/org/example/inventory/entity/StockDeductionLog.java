package org.example.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

import java.time.LocalDateTime;

@Data
@TableName("stock_deduction_log")
@NoArgsConstructor
public class StockDeductionLog {
    Long id;
    String OrderSn;
    LocalDateTime createdAt;

    public StockDeductionLog(String orderSn, LocalDateTime createdAt) {
        OrderSn = orderSn;
        this.createdAt = createdAt;
    }
}
