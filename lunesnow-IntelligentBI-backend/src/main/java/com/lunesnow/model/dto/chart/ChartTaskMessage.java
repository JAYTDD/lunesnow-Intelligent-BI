package com.lunesnow.model.dto.chart;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 图表任务消息
 * 用于 RabbitMQ 消息传递
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChartTaskMessage implements Serializable {

    /**
     * 图表 ID
     */
    private Long chartId;

    /**
     * 消息 ID（用于去重）
     */
    private String messageId;

    /**
     * 重试次数
     */
    private int retryCount = 0;
}
