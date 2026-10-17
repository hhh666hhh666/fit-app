package com.example.fitapp.module.plan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 计划内某个动作的**一组**目标。表结构以迁移脚本 {@code V26__plan_per_set_targets_and_circuit.sql} 为准。
 * 一行一组，动作内按 setIndex 排序；**组数的唯一真相是行数**（ADR 0027）。
 * 两个目标值都可为 null：只记次数、只记重量、或某一项临场再定都是合法用法。
 */
@Data
@TableName("plan_exercise_set")
public class PlanExerciseSet {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long planExerciseId;

    /** 组序号，1 起、动作内连续。 */
    private Integer setIndex;

    /** 该组目标次数；null = 该组未设。 */
    private Integer reps;

    /** 该组目标重量 kg；null = 该组未设。 */
    private BigDecimal weight;

    private LocalDateTime createdAt;
}
