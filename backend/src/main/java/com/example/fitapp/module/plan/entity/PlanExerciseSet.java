package com.example.fitapp.module.plan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 计划内某个动作的**一组**目标。表结构以迁移脚本 {@code V26__plan_per_set_targets_and_circuit.sql}
 * （建表）与 {@code V30__plan_set_floors.sql}（层数列）为准。
 * 一行一组，动作内按 setIndex 排序；**组数的唯一真相是行数**（ADR 0027）。
 * 三个目标值都可为 null：只记次数、只记重量、爬楼只记层数，或某一项临场再定都是合法用法。
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

    /**
     * 该组目标层数：仅 `metric = floors` 的动作（爬楼）使用，其余动作为 null。
     * 与 `workout_set.floors` 同构——层数不借用 reps，避免两套语义混在一个数里（ADR 0025／0031）。
     */
    private Integer floors;

    private LocalDateTime createdAt;
}
