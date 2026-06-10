package com.lunesnow.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lunesnow.model.dto.chart.ChartQueryRequest;
import com.lunesnow.model.entity.Chart;
import com.lunesnow.model.vo.ChartVO;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 图表服务
 */
public interface ChartService extends IService<Chart> {

    /**
     * 校验
     *
     * @param chart
     * @param add
     */
    void validChart(Chart chart, boolean add);

    /**
     * 获取查询条件
     *
     * @param chartQueryRequest
     * @return
     */
    QueryWrapper<Chart> getQueryWrapper(ChartQueryRequest chartQueryRequest);

    /**
     * 获取图表封装
     *
     * @param chart
     * @param request
     * @return
     */
    ChartVO getChartVO(Chart chart, HttpServletRequest request);

    /**
     * 分页获取图表封装
     *
     * @param chartPage
     * @param request
     * @return
     */
    Page<ChartVO> getChartVOPage(Page<Chart> chartPage, HttpServletRequest request);
}
