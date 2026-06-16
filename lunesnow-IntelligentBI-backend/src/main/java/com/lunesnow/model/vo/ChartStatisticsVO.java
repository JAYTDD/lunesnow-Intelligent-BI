package com.lunesnow.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 图表统计数据
 */
@Data
public class ChartStatisticsVO implements Serializable {

    /**
     * 图表总数
     */
    private Long totalCount;

    /**
     * 成功数量
     */
    private Long successCount;

    /**
     * 失败数量
     */
    private Long failedCount;

    /**
     * 运行中数量（waiting + running）
     */
    private Long runningCount;

    /**
     * 成功率（百分比）
     */
    private Double successRate;

    /**
     * 最近生成的图表（最多5条）
     */
    private List<ChartVO> recentCharts;

    private static final long serialVersionUID = 1L;
}
