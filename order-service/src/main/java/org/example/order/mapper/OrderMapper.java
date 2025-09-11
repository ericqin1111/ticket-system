package org.example.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.example.order.entity.Order;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {
    @Update("UPDATE orders " +
            "SET status = #{status} " +
            "where order_sn = #{orderSn}")
    public int updateStatus(@Param("orderSn") String orderSn, @Param("status") int status);
}
