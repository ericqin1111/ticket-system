package org.example.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.ticket.entity.PriceTier;

@Mapper
public interface PriceTierMapper extends BaseMapper<PriceTier> {
}
