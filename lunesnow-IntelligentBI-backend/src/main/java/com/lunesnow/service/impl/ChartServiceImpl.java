package com.lunesnow.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lunesnow.common.ErrorCode;
import com.lunesnow.constant.CommonConstant;
import com.lunesnow.exception.BusinessException;
import com.lunesnow.exception.ThrowUtils;
import com.lunesnow.mapper.ChartMapper;
import com.lunesnow.model.dto.chart.ChartQueryRequest;
import com.lunesnow.model.entity.Chart;
import com.lunesnow.model.entity.User;
import com.lunesnow.model.vo.ChartVO;
import com.lunesnow.model.vo.UserVO;
import com.lunesnow.service.ChartService;
import com.lunesnow.service.UserService;
import com.lunesnow.utils.SqlUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 图表服务实现
 */
@Service
@Slf4j
public class ChartServiceImpl extends ServiceImpl<ChartMapper, Chart> implements ChartService {

    @Resource
    private UserService userService;

    /**
     * 校验图表参数
     *
     * @param chart
     * @param add
     */
    @Override
    public void validChart(Chart chart, boolean add) {
        if (chart == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String goal = chart.getGoal();
        String chartType = chart.getChartType();
        // 创建时，参数不能为空
        if (add) {
            ThrowUtils.throwIf(StringUtils.isAnyBlank(goal, chartType), ErrorCode.PARAMS_ERROR);
        }
        // 有参数则校验
        if (StringUtils.isNotBlank(goal) && goal.length() > 200) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "分析目标过长");
        }
    }

    /**
     * 获取查询包装类
     *
     * @param chartQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<Chart> getQueryWrapper(ChartQueryRequest chartQueryRequest) {
        QueryWrapper<Chart> queryWrapper = new QueryWrapper<>();
        if (chartQueryRequest == null) {
            return queryWrapper;
        }
        Long id = chartQueryRequest.getId();
        String name = chartQueryRequest.getName();
        String goal = chartQueryRequest.getGoal();
        String chartType = chartQueryRequest.getChartType();
        Long userId = chartQueryRequest.getUserId();
        String sortField = chartQueryRequest.getSortField();
        String sortOrder = chartQueryRequest.getSortOrder();

        // 拼接查询条件
        queryWrapper.like(StringUtils.isNotBlank(name), "name", name);
        queryWrapper.like(StringUtils.isNotBlank(goal), "goal", goal);
        queryWrapper.like(StringUtils.isNotBlank(chartType), "chartType", chartType);
        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

    @Override
    public ChartVO getChartVO(Chart chart, HttpServletRequest request) {
        ChartVO chartVO = ChartVO.objToVo(chart);
        // 关联查询用户信息
        Long userId = chart.getUserId();
        User user = null;
        if (userId != null && userId > 0) {
            user = userService.getById(userId);
        }
        UserVO userVO = userService.getUserVO(user);
        chartVO.setUser(userVO);
        return chartVO;
    }

    @Override
    public Page<ChartVO> getChartVOPage(Page<Chart> chartPage, HttpServletRequest request) {
        // 获取当前页的图表实体列表
        List<Chart> chartList = chartPage.getRecords();
        // 创建用于返回的 ChartVO 分页对象，保持原有的分页参数
        Page<ChartVO> chartVOPage = new Page<>(chartPage.getCurrent(), chartPage.getSize(), chartPage.getTotal());
        
        // 如果列表为空，直接返回空的分页对象
        if (CollUtil.isEmpty(chartList)) {
            return chartVOPage;
        }

        // 提取所有图表关联的用户 ID 并去重
        Set<Long> userIdSet = chartList.stream().map(Chart::getUserId).collect(Collectors.toSet());
        
        // 批量查询用户信息，并按用户 ID 分组存入 Map
        // 注意：此处使用 listByIds 批量查询，避免了在循环中逐个查询数据库（解决了 N+1 问题）
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        
        // 遍历图表列表，执行转换并填充关联的用户信息
        List<ChartVO> chartVOList = chartList.stream().map(chart -> {
            // 将 Chart 实体转换为 ChartVO
            ChartVO chartVO = ChartVO.objToVo(chart);
            Long userId = chart.getUserId();
            User user = null;
            
            // 从 Map 中获取对应的用户信息
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            
            // 将 User 转换为 UserVO 并填充到 ChartVO 中
            chartVO.setUser(userService.getUserVO(user));
            return chartVO;
        }).collect(Collectors.toList());
        
        // 将转换后的列表设置到分页对象中并返回
        chartVOPage.setRecords(chartVOList);
        return chartVOPage;
    }

}
