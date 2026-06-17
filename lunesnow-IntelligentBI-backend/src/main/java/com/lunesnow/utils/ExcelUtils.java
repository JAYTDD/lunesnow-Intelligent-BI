package com.lunesnow.utils;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.lunesnow.common.ErrorCode;
import com.lunesnow.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Excel工具类
 */
@Slf4j
public class ExcelUtils {

    public static String excelToCsv(MultipartFile multipartFile) throws IOException {

        String fileName = multipartFile.getOriginalFilename();
        boolean isCsv = fileName != null && fileName.toLowerCase().endsWith(".csv");

        // CSV 文件直接读取返回
        if (isCsv) {
            try {
                String content = new String(multipartFile.getBytes(), "UTF-8");
                if (content.trim().isEmpty()) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "CSV 文件内容为空，请检查文件");
                }
                return content;
            } catch (BusinessException e) {
                throw e;
            } catch (IOException e) {
                log.error("读取 CSV 文件失败: {}", e.getMessage(), e);
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "CSV 文件读取失败，请确认文件编码为 UTF-8");
            }
        }

        // Excel 文件使用 EasyExcel 解析
        List<Map<Integer, String>> data = null;
        try {
            data = EasyExcel.read(multipartFile.getInputStream())
                    .excelType(ExcelTypeEnum.XLSX)
                    .sheet()
                    .headRowNumber(0)
                    .doReadSync();
        } catch (Exception e) {
            log.error("读取 Excel 文件失败: {}", e.getMessage(), e);
            // 区分不同错误类型
            String msg = e.getMessage();
            if (msg != null && msg.contains("not a valid OOXML")) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件格式损坏或不是有效的 Excel 文件");
            }
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Excel 文件读取失败，请确认文件格式正确");
        }
        //判断数据是否为空
        if (data == null || data.isEmpty()) {
            return "数据为空";
        }

        // 转换为CSV格式
        StringBuilder stringBuilder = new StringBuilder();
        LinkedHashMap<Integer, String> headMap = (LinkedHashMap<Integer, String>) data.get(0);
        stringBuilder.append(String.join(",", headMap.values())).append("\n");

        for (int i = 1; i < data.size(); i++) {
            LinkedHashMap<Integer, String> rowMap = (LinkedHashMap<Integer, String>) data.get(i);
            stringBuilder.append(String.join(",", rowMap.values())).append("\n");
        }
        return stringBuilder.toString();
    }
}
