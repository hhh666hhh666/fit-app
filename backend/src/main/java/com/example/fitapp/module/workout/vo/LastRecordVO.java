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

    private LocalDateTime performedAt;
}
