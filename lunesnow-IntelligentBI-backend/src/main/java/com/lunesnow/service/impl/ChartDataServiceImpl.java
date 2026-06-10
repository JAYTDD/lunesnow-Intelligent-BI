package com.lunesnow.service.impl;

import com.lunesnow.common.ErrorCode;
import com.lunesnow.exception.BusinessException;
import com.lunesnow.service.ChartDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import jakarta.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 图表数据服务实现
 * 直接将 Excel 文件内容复制到数据库表（表结构与 Excel 一致）
 *
 * @author lunesnow
 */
@Service
@Slf4j
public class ChartDataServiceImpl implements ChartDataService {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public void createTableFromCsv(Long chartId, String csvData) {
        if (csvData == null || csvData.isBlank()) {
            log.warn("CSV 数据为空，跳过创建表: chart_{}", chartId);
            return;
        }

        String tableName = "chart_" + chartId;
        String[] lines = csvData.split("\n");

        if (lines.length < 1) {
            log.warn("CSV 数据行数不足，跳过创建表: {}", tableName);
            return;
        }

        try {
            // 1. 解析列名（第一行）
            List<String> columns = parseColumns(lines[0]);
            if (columns.isEmpty()) {
                log.warn("没有有效的列名，跳过创建表: {}", tableName);
                return;
            }
            log.info("解析列名: {}", columns);

            // 2. 创建表（列名就是 Excel 的列名）
            createTable(tableName, columns);
            log.info("创建表 {} 完成", tableName);

            // 3. 插入数据
            insertData(tableName, columns, lines);
        } catch (Exception e) {
            log.error("创建表 {} 失败: {}", tableName, e.getMessage(), e);
            // 插入数据失败，删除已创建的表
            dropTable(chartId);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "图表数据导入失败");
        }
    }

    @Override
    public void dropTable(Long chartId) {
        String tableName = "chart_" + chartId;
        try {
            if (isTableExists(tableName)) {
                jdbcTemplate.execute("DROP TABLE IF EXISTS `" + tableName + "`");
                log.info("删除表 {} 完成", tableName);
            }
        } catch (Exception e) {
            log.error("删除表 {} 失败: {}", tableName, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public List<Map<String, String>> getTableData(Long chartId) {
        String tableName = "chart_" + chartId;

        try {
            // 检查表是否存在
            if (!isTableExists(tableName)) {
                log.warn("表 {} 不存在", tableName);
                return Collections.emptyList();
            }

            // 查询所有数据
            String sql = "SELECT * FROM `" + tableName + "`";
            List<Map<String, Object>> rawData = jdbcTemplate.queryForList(sql);

            // 转换格式
            return rawData.stream().map(row -> {
                Map<String, String> map = new LinkedHashMap<>();
                row.forEach((key, value) -> {
                    map.put(key, value != null ? value.toString() : null);
                });
                return map;
            }).collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        } catch (Exception e) {
            log.error("查询表 {} 数据失败: {}", tableName, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 创建表
     */
    private void createTable(String tableName, List<String> columns) {
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE IF NOT EXISTS `").append(tableName).append("` (");

        List<String> columnDefs = new ArrayList<>();
        for (String col : columns) {
            columnDefs.add("`" + col + "` VARCHAR(5000)");
        }

        sql.append(String.join(", ", columnDefs));
        sql.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        jdbcTemplate.execute(sql.toString());
    }

    /**
     * 插入数据
     */
    private void insertData(String tableName, List<String> columns, String[] lines) {
        if (lines.length < 2) {
            return;
        }

        // 构建 INSERT SQL
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO `").append(tableName).append("` (");

        // 列名部分
        List<String> colList = columns.stream()
                .map(col -> "`" + col + "`")
                .collect(Collectors.toList());
        sql.append(String.join(", ", colList));
        sql.append(") VALUES ");

        // 数据部分
        List<Object> params = new ArrayList<>();
        List<String> placeholders = new ArrayList<>();

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }

            String[] values = line.split(",", -1);
            StringBuilder placeholder = new StringBuilder("(");

            for (int j = 0; j < columns.size(); j++) {
                if (j > 0) {
                    placeholder.append(", ");
                }
                placeholder.append("?");
                params.add(j < values.length ? values[j].trim() : null);
            }
            placeholder.append(")");
            placeholders.add(placeholder.toString());
        }

        if (!placeholders.isEmpty()) {
            sql.append(String.join(", ", placeholders));
            jdbcTemplate.update(sql.toString(), params.toArray());
            log.info("插入数据到表 {} 完成，共 {} 行", tableName, placeholders.size());
        }
    }

    /**
     * 解析列名（第一行）
     * 过滤掉空列名和 "null" 字符串
     */
    private List<String> parseColumns(String headerLine) {
        return Arrays.stream(headerLine.split(","))
                .map(String::trim)
                .filter(s -> s != null && !s.isEmpty() && !"null".equalsIgnoreCase(s))
                .collect(Collectors.toList());
    }

    /**
     * 检查表是否存在
     */
    private boolean isTableExists(String tableName) {
        String sql = "SELECT COUNT(*) FROM information_schema.tables " +
                "WHERE table_schema = DATABASE() AND table_name = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, tableName);
        return count != null && count > 0;
    }
}
