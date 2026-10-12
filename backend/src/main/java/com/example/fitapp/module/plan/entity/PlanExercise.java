package com.example.fitapp.module.plan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 计划内的一条动作编排。表结构以迁移脚本 {@code V6__plan.sql} / {@code V9__plan_target_weight.sql} 为准。
 * name/muscle 为快照：显示不依赖 join 动作库（动作只下架不物删，快照更进一步兜底）。
 */
@Data
@TableName("plan_exercise")
public class PlanExercise {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long planId;

    private Long exerciseId;

    /** 动作名快照。 */
    private String name;

    /** 肌群码快照。 */
    private String muscle;

    /** 目标组数；null = 未设。 */
    private Integer targetSets;

    /** 目标每组次数；null = 未设。 */
    private Integer targetReps;

    /** 目标重量 kg；null = 未设（会话页预填负荷默认值用）。 */
    private BigDecimal targetWeight;

    private Integer sortOrder;

    private LocalDateTime createdAt;
}
