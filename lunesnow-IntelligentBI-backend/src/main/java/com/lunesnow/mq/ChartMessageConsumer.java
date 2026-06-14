package com.lunesnow.mq;

import com.lunesnow.config.RabbitConfig;
import com.lunesnow.config.DeepSeekUtils;
import com.lunesnow.model.dto.chart.ChartTaskMessage;
import com.lunesnow.model.entity.Chart;
import com.lunesnow.service.ChartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 图表任务消息消费者
 * 负责消费 RabbitMQ 中的图表生成任务
 */
@Component
@Slf4j
public class ChartMessageConsumer {

    /**
     * 任务超时时间：5分钟（毫秒）
     */
    private static final long TASK_TIMEOUT_MS = 5 * 60 * 1000L;

    @Resource
    private ChartService chartService;

    /**
     * 消费图表生成任务
     * queue = RabbitConfig.CHART_QUEUE 表示监听的队列名称
     * concurrentConsumers=4 表示同时处理 4 个任务
     */
    @RabbitListener(queues = RabbitConfig.CHART_QUEUE, concurrency = "4")
    public void handleChartTask(ChartTaskMessage message, @Header(AmqpHeaders.REDELIVERED) boolean redelivered) {
        Long chartId = message.getChartId();
        long startTime = System.currentTimeMillis();

        log.info("开始处理图表任务, chartId={}, redelivered={}", chartId, redelivered);

        try {
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

        } catch (Exception e) {
            long runningTime = System.currentTimeMillis() - startTime;
            log.error("图表生成任务失败, chartId={}", chartId, e);

            // 更新为 failed
            Chart updateFailed = new Chart();
            updateFailed.setId(chartId);
            updateFailed.setStatus("failed");
            updateFailed.setExecMessage(e.getMessage());
            updateFailed.setRunningTime(runningTime);
            chartService.updateById(updateFailed);
        }
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
