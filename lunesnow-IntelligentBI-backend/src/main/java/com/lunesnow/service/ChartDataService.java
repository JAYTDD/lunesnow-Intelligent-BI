package com.lunesnow.service;

import java.util.List;
import java.util.Map;

/**
 * 图表数据服务接口
 * 用于管理图表的原始数据（动态表）
 *
 * @author lunesnow
 */
public interface ChartDataService {

    /**
     * 将 CSV 数据创建为数据库表
     * 表名格式：chart_{chartId}
     *
     * @param chartId 图表ID
     * @param csvData CSV格式数据
     */
    void createTableFromCsv(Long chartId, String csvData);

    /**
     * 删除动态表
     *
     * @param chartId 图表ID
     */
    void dropTable(Long chartId);

    /**
     * 查询动态表数据
     *
     * @param chartId 图表ID
     * @return 数据列表（每行是一个 Map，key为列名，value为列值）
     */
    List<Map<String, String>> getTableData(Long chartId);
}
