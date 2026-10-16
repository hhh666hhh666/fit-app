package com.example.fitapp.module.workout.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 「我的」页的累计口径：全量训练次数与总容量。
 * 刻意由服务端聚合（而非拿封顶 200 条的列表求和冒充「累计」，见 ADR 0023）。
 */
@Data
public class WorkoutTotalsVO {

    /** 累计训练次数（全量）。 */
    private Integer count;

    /** 累计总容量 kg（全量；已按 ADR 0023 排除有氧）。 */
    private BigDecimal totalVolume;
}
