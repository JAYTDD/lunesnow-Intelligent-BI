package com.lunesnow.model.dto.chart;

import java.io.Serializable;
import lombok.Data;

/**
 * 编辑请求
 *
 * @author lunesnow
  */
@Data
public class ChartEditRequest implements Serializable {

    /**
     * id
     */
    private Long id;
    /**
     * 名称
     */
    private String name;
    /**
     * 分析目标
     */
    private String goal;

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
