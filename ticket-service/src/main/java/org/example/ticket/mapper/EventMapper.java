package org.example.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.ticket.entity.Event;

@Mapper
public interface EventMapper extends BaseMapper<Event> {
}
