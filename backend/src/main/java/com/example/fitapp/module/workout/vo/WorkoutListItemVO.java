package com.example.fitapp.module.workout.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 日志列表项（一条 = 一次训练）。
 */
@Data
public class WorkoutListItemVO {

    private Long id;

    private String title;

    private String firstExercise;

    private Integer durationSec;

    private BigDecimal totalVolume;

    private LocalDateTime createdAt;
}
