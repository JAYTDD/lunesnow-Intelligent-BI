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
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件表头为空，请确保第一行为列名");
            }
            log.info("解析列名: {}", columns);

            // 2. 创建表（列名就是 Excel 的列名）
            createTable(tableName, columns);
            log.info("创建表 {} 完成", tableName);

            // 3. 插入数据
            insertData(tableName, columns, lines);
        } catch (BusinessException e) {
            log.error("创建表 {} 失败: {}", tableName, e.getMessage(), e);
            dropTable(chartId);
            throw e;
        } catch (Exception e) {
            log.error("创建表 {} 失败: {}", tableName, e.getMessage(), e);
            dropTable(chartId);
            // 提供更精确的错误信息
            String msg = e.getMessage();
            if (msg != null && msg.contains("Row size too large")) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "数据列数过多或内容过长，建议减少列数");
            }
            if (msg != null && msg.contains("Duplicate column name")) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件中存在重复的列名");
            }
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "数据导入失败，请检查文件格式是否正确");
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
     * 根据过滤条件查询数据
     */
    @Override
    public List<Map<String, String>> getTableDataWithFilter(Long chartId, Map<String, String> filters) {
        String tableName = "chart_" + chartId;

        try {
            if (!isTableExists(tableName)) {
                return Collections.emptyList();
            }

            StringBuilder sql = new StringBuilder("SELECT * FROM `" + tableName + "`");
            List<Object> params = new ArrayList<>();

            // 构建 WHERE 条件
            if (filters != null && !filters.isEmpty()) {
                // 获取表的实际列名白名单
                List<String> actualColumns = getTableColumns(tableName);
                List<String> conditions = new ArrayList<>();
                for (Map.Entry<String, String> entry : filters.entrySet()) {
                    if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                        // 校验列名白名单，防止 SQL 注入
                        if (!actualColumns.contains(entry.getKey())) {
                            log.warn("忽略无效的筛选列名: {}", entry.getKey());
                            continue;
                        }
                        conditions.add("`" + entry.getKey() + "` LIKE ?");
                        params.add("%" + entry.getValue() + "%");
                    }
                }
                if (!conditions.isEmpty()) {
                    sql.append(" WHERE ").append(String.join(" AND ", conditions));
                }
            }

            List<Map<String, Object>> rawData = jdbcTemplate.queryForList(sql.toString(), params.toArray());

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
     * 获取列的唯一值
     */
    @Override
    public List<String> getColumnDistinctValues(Long chartId, String columnName) {
        String tableName = "chart_" + chartId;

        try {
            if (!isTableExists(tableName)) {
                return Collections.emptyList();
            }

            // 校验列名白名单，防止 SQL 注入
            validateColumnName(tableName, columnName);

            String sql = "SELECT DISTINCT `" + columnName + "` FROM `" + tableName + "` WHERE `" + columnName + "` IS NOT NULL ORDER BY `" + columnName + "`";
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);

            return result.stream()
                    .map(row -> row.get(columnName) != null ? row.get(columnName).toString() : null)
                    .filter(v -> v != null && !v.isEmpty())
                    .collect(Collectors.toList());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取列 {} 唯一值失败: {}", columnName, e.getMessage(), e);
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
            columnDefs.add("`" + col + "` VARCHAR(255)");
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
     * 过滤掉空列名和 "null" 字符串，并清理特殊字符防 SQL 注入
     */
    private List<String> parseColumns(String headerLine) {
        return Arrays.stream(headerLine.split(","))
                .map(String::trim)
                .filter(s -> s != null && !s.isEmpty() && !"null".equalsIgnoreCase(s))
                .map(ChartDataServiceImpl::sanitizeColumnName)
                .collect(Collectors.toList());
    }

    /**
     * 清理列名，仅保留字母、数字、下划线，防止 SQL 注入
     */
    private static String sanitizeColumnName(String name) {
        // 去掉所有非字母数字下划线的字符
        String cleaned = name.replaceAll("[^a-zA-Z0-9_\\u4e00-\\u9fa5]", "_");
        // 去掉前导数字
        if (cleaned.matches("^\\d.*")) {
            cleaned = "col_" + cleaned;
        }
        // 确保不为空
        if (cleaned.isEmpty()) {
            cleaned = "col_empty";
        }
        return cleaned;
    }

    /**
     * 校验列名是否在表中存在（白名单校验，防 SQL 注入）
     */
    private void validateColumnName(String tableName, String columnName) {
        List<String> actualColumns = getTableColumns(tableName);
        if (!actualColumns.contains(columnName)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "无效的列名: " + columnName);
        }
    }

    /**
     * 获取表的实际列名
     */
    private List<String> getTableColumns(String tableName) {
        String sql = "SELECT COLUMN_NAME FROM information_schema.COLUMNS " +
                "WHERE table_schema = DATABASE() AND table_name = ?";
        List<Map<String, Object>> result = jdbcTemplate.queryForList(sql, tableName);
        return result.stream()
                .map(row -> row.get("COLUMN_NAME").toString())
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
