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

    private BigDecimal totalVolume;

    private String note;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
