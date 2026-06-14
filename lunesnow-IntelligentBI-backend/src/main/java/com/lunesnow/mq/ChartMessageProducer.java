package com.lunesnow.mq;

import com.lunesnow.config.RabbitConfig;
import com.lunesnow.model.dto.chart.ChartTaskMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.UUID;

/**
 * 图表任务消息生产者
 * 负责发送图表生成任务到 RabbitMQ
 */
@Component
@Slf4j
public class ChartMessageProducer {

    @Resource
    private RabbitTemplate rabbitTemplate;

    /**
     * 发送图表生成任务
     *
     * @param chartId 图表 ID
     */
    public void sendChartTask(Long chartId) {
        ChartTaskMessage message = new ChartTaskMessage();
        message.setChartId(chartId);
        message.setMessageId(UUID.randomUUID().toString());
        message.setRetryCount(0);

        try {
            // 发送消息到 RabbitMQ 交换机
            rabbitTemplate.convertAndSend(
                    RabbitConfig.CHART_EXCHANGE,
                    RabbitConfig.CHART_ROUTING_KEY,
                    message
            );
            log.info("图表任务消息已发送, chartId={}, messageId={}", chartId, message.getMessageId());
        } catch (Exception e) {
            log.error("发送图表任务消息失败, chartId={}", chartId, e);
            throw e;
        }
    }
}
