package com.lunesnow.model.vo;

import lombok.Data;

/**
 * Bi 的返回结果
 */
@Data
public class BiResponse {

    private String genChart;

    private String genResult;

    private Long chartId;
    /**
     * 任务状态
     */
    private String status;

    /**
     * 执行信息
     */
    private String execMessage;
}
