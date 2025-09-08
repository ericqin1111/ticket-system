package org.example.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.example.inventory.entity.Stock;

@Mapper
public interface StockMapper extends BaseMapper<Stock> {

    @Update(
            "UPDATE stock SET quantity = quantity - #{quantity} " +
            "WHERE ticket_item_id = #{ticketItemId} AND quantity >= #{quantity}"
    )
    int deductStockInDB(@Param("ticketItemId")Long ticketItemId, @Param("quantity")Integer quantity);
}
