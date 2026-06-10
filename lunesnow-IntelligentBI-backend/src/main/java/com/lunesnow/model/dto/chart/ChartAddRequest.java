package com.lunesnow.model.dto.chart;

import java.io.Serializable;
import lombok.Data;

/**
 * 创建请求
 *
 * @author lunesnow
  */
@Data
public class ChartAddRequest implements Serializable {

    /**
     * 分析目标
     */
    private String goal;

    /**
     * 名称
     */
    private String name;

    /**
     * 图表数据
     */
    private String chartData;

    /**
     * 图表类型
     */
    private String chartType;

    private static final long serialVersionUID = 1L;
}
