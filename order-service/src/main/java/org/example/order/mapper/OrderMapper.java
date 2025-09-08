package org.example.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.example.order.entity.Order;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {
}
