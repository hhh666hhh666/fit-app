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

    /**
     * 该次训练是否含**力量动作**（有氧不计入容量，ADR 0023）。
     * 纯有氧场次的容量位要显示「—」而不是 `0 kg`——后者看着像故障。
     */
    private Boolean hasStrength;

    /**
     * 该次训练各组的层数合计（`metric = floors` 的动作；没有则 0）。
     * 成绩位口径见 ADR 0025：有力量动作看容量，纯有氧看层数，都没有才是「—」。
     */
    private Integer totalFloors;

    private LocalDateTime createdAt;
}
