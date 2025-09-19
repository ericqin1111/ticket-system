package org.example.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.inventory.entity.StockDeductionLog;

@Mapper
public interface StockDeductionMapper extends BaseMapper<StockDeductionLog> {
}
