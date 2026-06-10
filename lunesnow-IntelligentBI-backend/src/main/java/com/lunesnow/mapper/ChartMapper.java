package com.lunesnow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lunesnow.model.entity.Chart;
import org.apache.ibatis.annotations.Mapper;

/**
 * 图表数据库操作
 *
 * @author lunesnow
 */
@Mapper
public interface ChartMapper extends BaseMapper<Chart> {

}
