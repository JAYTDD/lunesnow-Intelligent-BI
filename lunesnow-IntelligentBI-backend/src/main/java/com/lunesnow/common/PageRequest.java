package com.lunesnow.common;

import com.lunesnow.constant.CommonConstant;
import lombok.Data;

/**
 * 分页请求
 *
 * @author lunesnow
  */
@Data
public class PageRequest {

    /**
     * 当前页号
     */
    private int current = 1;

    /**
     * 页面大小
     */
    private int pageSize = 10;

    /**
     * 排序字段
     */
    private String sortField;

    /**
     * 排序顺序（默认升序）
     */
    private String sortOrder = CommonConstant.SORT_ORDER_ASC;

    public int getCurrent() {
        return Math.max(current, 1);
    }

    public int getPageSize() {
        return Math.min(Math.max(pageSize, 1), 100);
    }
}
