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
import com.lunesnow.mq.ChartMessageProducer;
import com.lunesnow.model.entity.Chart;
import com.lunesnow.model.entity.User;
import com.lunesnow.model.enums.FileUploadBizEnum;
import com.lunesnow.model.vo.BiResponse;
import com.lunesnow.model.vo.ChartStatisticsVO;
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
    private ChartMessageProducer chartMessageProducer;

    @Resource
    private com.lunesnow.manager.ChartTaskLimiter chartTaskLimiter;

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
     * 编辑图表配置（ECharts 配置代码）
     *
     * @param chartEditConfigRequest
     * @param request
     * @return
     */
    @PostMapping("/edit/config")
    public BaseResponse<Boolean> editChartConfig(@RequestBody ChartEditConfigRequest chartEditConfigRequest,
                                                 HttpServletRequest request) {
        if (chartEditConfigRequest == null || chartEditConfigRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        if (StringUtils.isBlank(chartEditConfigRequest.getGenChart())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图表配置不能为空");
        }
        User loginUser = userService.getLoginUser(request);
        long id = chartEditConfigRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldChart.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 只更新 genChart 字段
        Chart chart = new Chart();
        chart.setId(id);
        chart.setGenChart(chartEditConfigRequest.getGenChart());
        boolean result = chartService.updateById(chart);
        return ResultUtils.success(result);
    }

    /**
     * 获取图表统计数据（当前用户）
     *
     * @param request
     * @return
     */
    @GetMapping("/statistics")
    public BaseResponse<ChartStatisticsVO> getStatistics(HttpServletRequest request) {
        ChartStatisticsVO statistics = chartService.getStatistics(request);
        return ResultUtils.success(statistics);
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
            permitsPerSecond = 2,   // 每秒允许2个请求
            burstCapacity = 5,      // 最大并发数5个
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

        // 检查用户任务数量限制
        if (!chartTaskLimiter.tryAcquire(loginUser.getId())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "您当前有任务正在执行，请稍后再试");
        }

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

        // 3. 发送消息到 RabbitMQ
        final Long chartId = chart.getId();
        try {
            chartMessageProducer.sendChartTask(chartId);
        } catch (Exception e) {
            // 发送失败，标记任务失败
            log.warn("发送图表任务消息失败, chartId={}", chartId);
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

        // 重置状态为 waiting
        Chart updateWaiting = new Chart();
        updateWaiting.setId(id);
        updateWaiting.setStatus("waiting");
        updateWaiting.setGenChart(null);
        updateWaiting.setGenResult(null);
        updateWaiting.setExecMessage(null);
        chartService.updateById(updateWaiting);

        // 发送消息到 RabbitMQ
        try {
            chartMessageProducer.sendChartTask(id);
        } catch (Exception e) {
            // 发送失败，标记任务失败
            log.warn("发送重试任务消息失败, chartId={}", id);
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
