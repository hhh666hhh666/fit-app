package com.example.fitapp.module.plan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 周训练安排的一格。表结构以迁移脚本 {@code V6__plan.sql} 为准。
 * 语义：plan_id 有值 = 练该计划；is_rest=1 = 休息；无行 = 未安排。
 */
@Data
@TableName("week_schedule")
public class WeekSchedule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 周几：0=周一 … 6=周日。 */
    private Integer dayOfWeek;

    /** 安排的计划；与 is_rest=1 互斥。 */
    private Long planId;

    /** 1=休息日。 */
    private Boolean isRest;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
