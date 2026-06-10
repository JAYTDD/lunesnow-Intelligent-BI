package com.lunesnow.model.dto.chart;


import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import java.io.Serializable;


@Data
public class GenChartByAIRequest implements Serializable {

    /**
     * 名称
     */
    private String name;

    /**
     * 分析目标
     */
    private String goal;


    /**
     * 图表类型
     */
    private String chartType;



    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
