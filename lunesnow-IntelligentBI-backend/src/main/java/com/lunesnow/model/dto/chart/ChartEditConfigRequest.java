package com.lunesnow.model.dto.chart;

import java.io.Serializable;
import lombok.Data;

/**
 * 编辑图表配置请求
 */
@Data
public class ChartEditConfigRequest implements Serializable {

    /**
     * 图表 id
     */
    private Long id;

    /**
     * ECharts 配置 JSON
     */
    private String genChart;

    private static final long serialVersionUID = 1L;
}
