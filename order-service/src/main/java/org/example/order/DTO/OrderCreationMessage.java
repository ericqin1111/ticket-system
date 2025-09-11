package org.example.order.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用于订单创建的 Kafka 消息体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreationMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 票品项ID (场次ID)
     */
    private Long ticketItemId;

    /**
     * 购买数量
     */
    private Integer quantity;

    /**
     * 订单唯一编号，用于幂等性处理和状态追踪
     */
    private String orderSerialNo;
}

