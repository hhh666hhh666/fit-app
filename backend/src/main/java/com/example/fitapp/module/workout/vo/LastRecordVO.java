package com.example.fitapp.module.workout.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 某动作的「上次数据」（最近一次做该动作的一组）。
 */
@Data
public class LastRecordVO {

    private BigDecimal weight;

    private Integer reps;

    /** 层数（`metric=floors` 的动作有值，其余为 null）——「上次数据」提示要用它。 */
    private Integer floors;

    private LocalDateTime performedAt;
}
