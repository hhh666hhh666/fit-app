package com.example.fitapp.module.workout.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 一次训练。表结构以迁移脚本 {@code V4__workout.sql} 为准。
 */
@Data
@TableName("workout")
public class Workout {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 来源计划（弱引用，自由训练为 null；删计划不影响历史）。 */
    private Long planId;

    /** 当次标题快照（计划名或「自由训练」）。 */
    private String title;

    private LocalDateTime startedAt;

    private Integer durationSec;

    /**
     * 本次训练消耗的热量（大卡，主人手填；null＝未填）。见 ADR 0029——
     * 只有填了才计入当日总消耗，系统不估算。
     */
    private Integer calories;

    private BigDecimal totalVolume;

    private String note;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
