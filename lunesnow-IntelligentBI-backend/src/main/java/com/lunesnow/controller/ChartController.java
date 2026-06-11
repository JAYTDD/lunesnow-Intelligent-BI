package com.lunesnow.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSON;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lunesnow.annotation.AuthCheck;
import com.lunesnow.annotation.RateLimit;
import com.lunesnow.common.BaseResponse;
import com.lunesnow.common.DeleteRequest;
import com.lunesnow.common.ErrorCode;
import com.lunesnow.common.ResultUtils;
import com.lunesnow.config.DeepSeekUtils;
import com.lunesnow.constant.UserConstant;
import com.lunesnow.exception.BusinessException;
import com.lunesnow.exception.ThrowUtils;
import com.lunesnow.model.dto.chart.*;
import com.lunesnow.model.dto.file.UploadFileRequest;
import com.lunesnow.model.entity.Chart;
import com.lunesnow.model.entity.User;
import com.lunesnow.model.enums.FileUploadBizEnum;
import com.lunesnow.model.vo.BiResponse;
import com.lunesnow.model.vo.ChartVO;
import com.lunesnow.service.ChartService;
import com.lunesnow.service.ChartDataService;
import com.lunesnow.service.UserService;
import com.lunesnow.utils.ExcelUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 图表接口

 */
@RestController
@RequestMapping("/chart")
@Slf4j
public class ChartController {

    @Resource
    private ChartService chartService;

    @Resource
    private ChartDataService chartDataService;

    @Resource
    private UserService userService;

    @Resource
    private ThreadPoolExecutor chartTaskExecutor;

    // region 增删改查

    /**
     * 创建
     *
     * @param chartAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addChart(@RequestBody ChartAddRequest chartAddRequest, HttpServletRequest request) {
        if (chartAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartAddRequest, chart);
        chartService.validChart(chart, true);
        User loginUser = userService.getLoginUser(request);
        chart.setUserId(loginUser.getId());
        boolean result = chartService.save(chart);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        long newChartId = chart.getId();
        return ResultUtils.success(newChartId);
    }

    /**
     * 删除
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteChart(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldChart.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 删除动态表
        chartDataService.dropTable(id);
        // 删除图表记录
        boolean b = chartService.removeById(id);
        return ResultUtils.success(b);
    }

    /**
     * 更新（仅管理员）
     *
     * @param chartUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateChart(@RequestBody ChartUpdateRequest chartUpdateRequest) {
        if (chartUpdateRequest == null || chartUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartUpdateRequest, chart);
        // 参数校验
        chartService.validChart(chart, false);
        long id = chartUpdateRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = chartService.updateById(chart);
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 获取
     *
     * @param id
     * @return
     */
    @GetMapping("/get/vo")
    public BaseResponse<ChartVO> getChartVOById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = chartService.getById(id);
        if (chart == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return ResultUtils.success(chartService.getChartVO(chart, request));
    }

    /**
     * 分页获取列表（仅管理员）
     *
     * @param chartQueryRequest
     * @return
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Chart>> listChartByPage(@RequestBody ChartQueryRequest chartQueryRequest) {
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        Page<Chart> chartPage = chartService.page(new Page<>(current, size),
                chartService.getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);
    }

    /**
     * 分页获取列表（封装类）
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<ChartVO>> listChartVOByPage(@RequestBody ChartQueryRequest chartQueryRequest,
            HttpServletRequest request) {
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Chart> chartPage = chartService.page(new Page<>(current, size),
                chartService.getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartService.getChartVOPage(chartPage, request));
    }

    /**
     * 分页获取当前用户创建的资源列表
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<ChartVO>> listMyChartVOByPage(@RequestBody ChartQueryRequest chartQueryRequest,
            HttpServletRequest request) {
        if (chartQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        chartQueryRequest.setUserId(loginUser.getId());
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Chart> chartPage = chartService.page(new Page<>(current, size),
                chartService.getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartService.getChartVOPage(chartPage, request));
    }

    /**
     * 编辑（用户）
     *
     * @param chartEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editChart(@RequestBody ChartEditRequest chartEditRequest, HttpServletRequest request) {
        if (chartEditRequest == null || chartEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartEditRequest, chart);
        // 参数校验
        chartService.validChart(chart, false);
        User loginUser = userService.getLoginUser(request);
        long id = chartEditRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldChart.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = chartService.updateById(chart);
        return ResultUtils.success(result);
    }

    /**
     * 智能分析（异步任务）
     *
     * @param multipartFile
     * @param name      图表名称
     * @param chartType 图表类型
     * @param goal      分析目标或需求
     * @param request
     * @return
     */
    @PostMapping("/gen")
    @RateLimit(
            permitsPerSecond = 2,
            burstCapacity = 5,
            limitType = RateLimit.LimitType.USER,
            message = "AI 图表生成请求过于频繁，请稍后再试"
    )
    public BaseResponse<BiResponse> getChartByAI(@RequestPart("file") MultipartFile multipartFile,
                                                @RequestParam String name,
                                                @RequestParam String chartType,
                                                @RequestParam String goal,
                                                HttpServletRequest request) throws IOException {

        // 参数校验
        ThrowUtils.throwIf(StringUtils.isAnyBlank(name, goal), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(multipartFile.isEmpty(), ErrorCode.PARAMS_ERROR);
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        // 校验文件大小
        ThrowUtils.throwIf(multipartFile.getSize() > 1024 * 1024 * 2, ErrorCode.PARAMS_ERROR, "文件大小不能超过2M");
        // 校验文件后缀
        String fileName = multipartFile.getOriginalFilename();
        String fileSuffix = FileUtil.getSuffix(fileName);
        final List<String> allowedSuffixes = Arrays.asList("xlsx", "xls");
        ThrowUtils.throwIf(!allowedSuffixes.contains(fileSuffix), ErrorCode.PARAMS_ERROR, "文件校验失败");

        // 转换为 CSV
        String csvData = ExcelUtils.excelToCsv(multipartFile);

        // 1. 先保存图表到数据库，状态为 waiting
        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(goal);
        chart.setChartType(chartType);
        chart.setUserId(loginUser.getId());
        chart.setStatus("waiting");
        chart.setChartData(csvData);
        boolean saveResult = chartService.save(chart);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "图表保存失败");

        // 2. 将上传的文件创建为数据库表
        chartDataService.createTableFromCsv(chart.getId(), csvData);

        // 3. 提交到线程池异步执行 AI 生成
        final Long chartId = chart.getId();
        final String userGoal = goal + (StringUtils.isNotBlank(chartType) ? "，请使用" + chartType : "");
        try {
            chartTaskExecutor.execute(() -> executeChartGenTask(chartId, userGoal));
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // 线程池满，标记任务失败
            log.warn("线程池已满，图表任务被拒绝, chartId={}", chartId);
            Chart updateFailed = new Chart();
            updateFailed.setId(chartId);
            updateFailed.setStatus("failed");
            updateFailed.setExecMessage("系统繁忙，请稍后重试");
            chartService.updateById(updateFailed);
        }

        // 4. 立即返回 chartId
        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(chartId);
        return ResultUtils.success(biResponse);
    }

    /**
     * 任务超时时间：5分钟（毫秒）
     */
    private static final long TASK_TIMEOUT_MS = 5 * 60 * 1000;

    /**
     * 执行图表生成任务（在线程池中运行）
     */
    private void executeChartGenTask(Long chartId, String userGoal) {
        long startTime = System.currentTimeMillis();
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

            // 检查是否已超时
            if (System.currentTimeMillis() - startTime > TASK_TIMEOUT_MS) {
                throw new RuntimeException("任务执行超时（超过5分钟）");
            }

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
            userInput.append(userGoal).append("\n");
            userInput.append("原始数据：\n");
            // 从数据库重新获取 csvData
            Chart chartWithCsv = chartService.getById(chartId);
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

    /**
     * 重新生成图表（仅限 failed 状态）
     */
    @PostMapping("/retry/{id}")
    public BaseResponse<BiResponse> retryChartGen(@PathVariable Long id, HttpServletRequest request) {
        // 校验图表存在
        Chart chart = chartService.getById(id);
        ThrowUtils.throwIf(chart == null, ErrorCode.NOT_FOUND_ERROR, "图表不存在");

        // 校验权限：只能操作自己的图表
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(!chart.getUserId().equals(loginUser.getId()), ErrorCode.FORBIDDEN_ERROR, "无权操作");

        // 校验状态：只能重试 failed 状态
        ThrowUtils.throwIf(!"failed".equals(chart.getStatus()), ErrorCode.PARAMS_ERROR, "只能重新生成失败的图表");

        // 构造目标
        String userGoal = chart.getGoal();
        if (StringUtils.isNotBlank(chart.getChartType())) {
            userGoal += "，请使用" + chart.getChartType();
        }
        final String finalUserGoal = userGoal;

        // 重置状态为 waiting
        Chart updateWaiting = new Chart();
        updateWaiting.setId(id);
        updateWaiting.setStatus("waiting");
        updateWaiting.setGenChart(null);
        updateWaiting.setGenResult(null);
        updateWaiting.setExecMessage(null);
        chartService.updateById(updateWaiting);

        // 提交到线程池
        try {
            chartTaskExecutor.execute(() -> executeChartGenTask(id, finalUserGoal));
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // 线程池满，标记任务失败
            log.warn("线程池已满，重试任务被拒绝, chartId={}", id);
            Chart updateFailed = new Chart();
            updateFailed.setId(id);
            updateFailed.setStatus("failed");
            updateFailed.setExecMessage("系统繁忙，请稍后重试");
            chartService.updateById(updateFailed);
        }

        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(id);
        return ResultUtils.success(biResponse);
    }

    /**
     * 查询图表任务状态
     */
    @GetMapping("/status/{id}")
    public BaseResponse<BiResponse> getChartStatus(@PathVariable Long id, HttpServletRequest request) {
        Chart chart = chartService.getById(id);
        ThrowUtils.throwIf(chart == null, ErrorCode.NOT_FOUND_ERROR, "图表不存在");

        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(!chart.getUserId().equals(loginUser.getId()), ErrorCode.FORBIDDEN_ERROR, "无权操作");

        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(chart.getId());
        biResponse.setStatus(chart.getStatus());
        biResponse.setExecMessage(chart.getExecMessage());
        biResponse.setGenChart(chart.getGenChart());
        biResponse.setGenResult(chart.getGenResult());
        return ResultUtils.success(biResponse);
    }

    /**
     * 获取图表原始数据
     *
     * @param chartId 图表ID
     * @param request
     * @return
     */
    @GetMapping("/get/data/{chartId}")
    public BaseResponse<List<Map<String, String>>> getChartData(
            @PathVariable Long chartId,
            HttpServletRequest request) {
        if (chartId == null || chartId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 权限校验
        User loginUser = userService.getLoginUser(request);
        Chart chart = chartService.getById(chartId);
        if (chart == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        if (!chart.getUserId().equals(loginUser.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        // 查询动态表数据
        List<Map<String, String>> data = chartDataService.getTableData(chartId);
        return ResultUtils.success(data);
    }
}
