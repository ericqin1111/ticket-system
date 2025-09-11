package org.example.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.order.entity.Outbox;

@Mapper
public interface OutboxMapper extends BaseMapper<Outbox> {
}
