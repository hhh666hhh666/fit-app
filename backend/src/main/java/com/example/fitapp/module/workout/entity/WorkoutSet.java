package com.example.fitapp.module.workout.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 组次：某个动作条目下的一组数据（重量 × 次数）。
 */
@Data
@TableName("workout_set")
public class WorkoutSet {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long blockId;

    private Integer setIndex;

    private BigDecimal weight;

    private Integer reps;

    private LocalDateTime createdAt;
}
