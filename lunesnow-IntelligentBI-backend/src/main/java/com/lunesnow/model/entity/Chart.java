package com.lunesnow.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 图表
 *
 * @author lunesnow
  */
@TableName(value = "chart")
@Data
public class Chart implements Serializable {

    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 名称
     */
    private String name;

    /**
     * 分析目标
     */
    private String goal;

    /**
     * 图表类型
     */
    private String chartType;
    /**
     * 生成的图表数据
     */
    private String genChart;
    /**
     * 生成的分析结论
     */
    private String genResult;

    /**
     * 任务状态: waiting / running / succeed / failed
     */
    private String status;

    /**
     * 执行信息（执行中或失败时的详细信息）
     */
    private String execMessage;

    /**
     * 等待时间（毫秒）
     */
    private Long waitTime;

    /**
     * 执行时间（毫秒）
     */
    private Long runningTime;

    /**
     * 原始 CSV 数据（用于重新生成）
     */
    private String chartData;

    /**
     * 创建用户 id
     */
    private Long userId;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 是否删除
     */
    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}