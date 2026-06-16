package com.lunesnow.mq;

import com.lunesnow.config.RabbitConfig;
import com.lunesnow.config.DeepSeekUtils;
import com.lunesnow.model.dto.chart.ChartTaskMessage;
import com.lunesnow.model.entity.Chart;
import com.lunesnow.service.ChartService;
import com.lunesnow.websocket.ChartWebSocketHandler;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.io.IOException;

/**
 * 图表任务消息消费者
 * 负责消费 RabbitMQ 中的图表生成任务
 * 失败消息自动进入死信队列
 */
@Component
@Slf4j
public class ChartMessageConsumer {

    /**
     * 任务超时时间：5分钟（毫秒）
     */
    private static final long TASK_TIMEOUT_MS = 5 * 60 * 1000L;

    /**
     * 最大重试次数
     */
    private static final int MAX_RETRY_COUNT = 3;

    @Resource
    private ChartService chartService;

    @Resource
    private com.lunesnow.manager.ChartTaskLimiter chartTaskLimiter;

    @Resource
    private ChartWebSocketHandler chartWebSocketHandler;

    /**
     * 消费图表生成任务
     * 手动 ACK 模式，处理成功后确认，失败则拒绝（进入死信队列）
     */
    @RabbitListener(queues = RabbitConfig.CHART_QUEUE, concurrency = "4")
    public void handleChartTask(ChartTaskMessage message, Channel channel,
                                @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                                @Header(AmqpHeaders.REDELIVERED) boolean redelivered) {
        Long chartId = message.getChartId();
        long startTime = System.currentTimeMillis();

        log.info("开始处理图表任务, chartId={}, redelivered={}, retryCount={}",
                chartId, redelivered, message.getRetryCount());

        try {
            // 检查重试次数
            if (message.getRetryCount() >= MAX_RETRY_COUNT) {
                log.warn("图表任务超过最大重试次数, chartId={}, retryCount={}", chartId, message.getRetryCount());
                handleFailedTask(chartId, startTime, "超过最大重试次数(" + MAX_RETRY_COUNT + ")");
                // 释放任务槽位
                try {
                    Chart retryChart = chartService.getById(chartId);
                    if (retryChart != null) {
                        chartTaskLimiter.release(retryChart.getUserId());
                    }
                } catch (Exception ex) {
                    log.error("释放任务槽位异常: chartId={}", chartId, ex);
                }
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 更新状态为 running
            Chart updateRunning = new Chart();
            updateRunning.setId(chartId);
            updateRunning.setStatus("running");
            Chart existingChart = chartService.getById(chartId);
            if (existingChart != null) {
                updateRunning.setWaitTime(startTime - existingChart.getCreateTime().getTime());
            }
            chartService.updateById(updateRunning);

            // 构造 prompt
            final String prompt = "你是一个数据分析师和前端开发专家，接下来我会按照以下固定格式给你提供内容：\n" +
                    "分析需求：\n" +
                    "{数据分析的需求或者目标}\n" +
                    "原始数据：\n" +
                    "{csv格式的原始数据，用,作为分隔符}\n" +
                    "请根据这两部分内容，按照以下指定格式生成内容（此外不要输出任何多余的开头、结尾、注释）\n" +
                    "【【【【【\n" +
                    "{前端 Echarts V5 的 option 配置对象js代码，合理地将数据进行可视化，不要生成任何多余的内容，比如注释}\n" +
                    "【【【【【\n" +
                    "{明确的数据分析结论、越详细越好，不要生成多余的注释}";

            StringBuilder userInput = new StringBuilder();
            userInput.append("分析需求：\n");

            // 从数据库获取图表信息
            Chart chartWithCsv = chartService.getById(chartId);
            String userGoal = chartWithCsv.getGoal();
            if (chartWithCsv.getChartType() != null && !chartWithCsv.getChartType().isEmpty()) {
                userGoal += "，请使用" + chartWithCsv.getChartType();
            }
            userInput.append(userGoal).append("\n");
            userInput.append("原始数据：\n");
            userInput.append(chartWithCsv.getChartData()).append("\n");

            // 调用 DeepSeek AI
            String aiResponse = DeepSeekUtils.generateContent(prompt, userInput.toString());

            // 检查是否已超时
            if (System.currentTimeMillis() - startTime > TASK_TIMEOUT_MS) {
                throw new RuntimeException("任务执行超时（超过5分钟）");
            }

            // 解析 AI 响应
            String[] parts = aiResponse.split("【【【【【");
            String genChart = parts.length > 1 ? parts[1].trim() : "";
            genChart = genChart.replaceAll("(?s)```(?:javascript|js)?\\s*", "").trim();
            genChart = genChart.replaceFirst("^(?:let|var|const)?\\s*option\\s*=\\s*", "");
            if (genChart.endsWith(";")) genChart = genChart.substring(0, genChart.length() - 1).trim();
            String genResult = parts.length > 2 ? parts[2].trim() : aiResponse;

            // 验证 AI 生成结果的有效性
            validateAiResult(genChart, genResult);

            long runningTime = System.currentTimeMillis() - startTime;

            // 更新为 succeed
            Chart updateSuccess = new Chart();
            updateSuccess.setId(chartId);
            updateSuccess.setStatus("succeed");
            updateSuccess.setExecMessage("success");
            updateSuccess.setGenChart(genChart);
            updateSuccess.setGenResult(genResult);
            updateSuccess.setRunningTime(runningTime);
            chartService.updateById(updateSuccess);

            log.info("图表生成任务完成, chartId={}, 耗时={}ms", chartId, runningTime);

            // 释放任务槽位
            Chart chart = chartService.getById(chartId);
            if (chart != null) {
                chartTaskLimiter.release(chart.getUserId());

                // WebSocket 推送成功通知
                chartWebSocketHandler.notifyChartSuccess(
                        chart.getUserId(), chartId, chart.getName());
            }

            // 处理成功，确认消息
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            long runningTime = System.currentTimeMillis() - startTime;
            log.error("图表生成任务失败, chartId={}", chartId, e);

            // 更新为 failed
            handleFailedTask(chartId, startTime, e.getMessage());

            // 释放任务槽位
            try {
                Chart failChart = chartService.getById(chartId);
                if (failChart != null) {
                    chartTaskLimiter.release(failChart.getUserId());

                    // WebSocket 推送失败通知
                    chartWebSocketHandler.notifyChartFailure(
                            failChart.getUserId(), chartId, failChart.getName(), e.getMessage());
                }
            } catch (Exception ex) {
                log.error("释放任务槽位异常: chartId={}", chartId, ex);
            }

            try {
                // 拒绝消息，requeue=false 表示不重新入队，直接进入死信队列
                channel.basicNack(deliveryTag, false, false);
                log.info("消息已拒绝并进入死信队列, chartId={}", chartId);
            } catch (IOException ioException) {
                log.error("拒绝消息失败, chartId={}", chartId, ioException);
            }
        }
    }

    /**
     * 处理失败任务
     */
    private void handleFailedTask(Long chartId, long startTime, String errorMessage) {
        long runningTime = System.currentTimeMillis() - startTime;
        Chart updateFailed = new Chart();
        updateFailed.setId(chartId);
        updateFailed.setStatus("failed");
        updateFailed.setExecMessage(errorMessage);
        updateFailed.setRunningTime(runningTime);
        chartService.updateById(updateFailed);
    }

    /**
     * 消费死信队列（记录失败任务）
     * 死信队列是"最终处理者"，处理完消息自动删除
     */
    @RabbitListener(queues = RabbitConfig.DEAD_LETTER_QUEUE)
    public void handleDeadLetter(ChartTaskMessage message) {
        Long chartId = message.getChartId();
        log.error("死信队列收到失败任务: chartId={}, messageId={}",
                chartId, message.getMessageId());

        // 查询当前图表状态
        Chart chart = chartService.getById(chartId);

        // 情况 1：图表已删除 → 忽略
        if (chart == null) {
            log.info("图表已删除，忽略死信消息: chartId={}", chartId);
            return;
        }

        // 情况 2：图表已成功（用户重新生成了）→ 忽略旧消息
        if ("succeed".equals(chart.getStatus())) {
            log.info("图表已成功，忽略旧的死信消息: chartId={}", chartId);
            return;
        }

        // 情况 3：图表正在重新生成 → 等待
        if ("waiting".equals(chart.getStatus()) || "running".equals(chart.getStatus())) {
            log.info("图表正在重新生成，暂存死信消息: chartId={}", chartId);
            return;
        }

        // 情况 4：图表仍然是 failed → 告警通知
        log.warn("【告警】图表任务失败，需要人工处理: chartId={}", chartId);
        // TODO: 可以在这里发送告警通知（钉钉/邮件/短信）
    }

    /**
     * 验证 AI 生成结果的有效性
     */
    private void validateAiResult(String genChart, String genResult) {
        // 1. 检查图表配置是否为空
        if (genChart == null || genChart.isEmpty()) {
            throw new RuntimeException("AI 未生成有效的图表配置");
        }

        // 2. 检查图表配置是否包含基本的 ECharts 配置
        if (!genChart.contains("type") && !genChart.contains("series") && !genChart.contains("data")) {
            throw new RuntimeException("AI 生成的图表配置格式不正确，缺少必要的 ECharts 配置项");
        }

        // 3. 检查分析结果是否为空
        if (genResult == null || genResult.isEmpty()) {
            throw new RuntimeException("AI 未生成有效的分析结论");
        }

        // 4. 检查分析结果长度（至少10个字符）
        if (genResult.length() < 10) {
            throw new RuntimeException("AI 生成的分析结论过于简短，可能不是有效的分析结果");
        }
    }
}
