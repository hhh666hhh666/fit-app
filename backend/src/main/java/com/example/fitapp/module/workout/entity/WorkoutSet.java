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

    /** 重量 kg */
    private BigDecimal weight;

    /** 次数（`metric=floors` 的动作存占位值 1，见 ADR 0025） */
    private Integer reps;

    /** 层数：`metric=floors` 的动作专用；其他动作与历史数据为 null。 */
    private Integer floors;

    /** 提交时是否刷新该动作历史最佳（weight×reps；仅 exercise_id 非空时判定）。 */
    private Boolean pr;

    /** 该组净用时（秒，剔除暂停段）；无用时为 null（历史数据与编辑时新增的组）。 */
    private Integer durationSec;

    private LocalDateTime createdAt;
}
