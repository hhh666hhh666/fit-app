package com.example.fitapp.module.workout.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 某动作的历史最大单组容量（weight×reps）——PR 判定的历史起点。
 */
@Data
public class ExerciseMaxVO {

    private Long exerciseId;

    private BigDecimal maxVol;
}
