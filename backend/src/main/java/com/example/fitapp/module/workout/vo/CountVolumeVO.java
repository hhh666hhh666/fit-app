package com.example.fitapp.module.workout.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 某个月的次数与总容量（日志页的按月概览用）。
 * 容量口径已按 ADR 0023 排除有氧，所以「有记录但容量为 0」＝当月只有有氧。
 */
@Data
public class CountVolumeVO {

    private Integer cnt;

    /** 总容量 kg（SUM(total_volume)，空月份为 0）。 */
    private BigDecimal vol;
}
