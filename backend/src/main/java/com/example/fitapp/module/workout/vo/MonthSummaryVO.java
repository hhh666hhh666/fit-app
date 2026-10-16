package com.example.fitapp.module.workout.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 按月（＆本周）的轻量概览：当月次数与总容量、**上月同口径**，外加本周次数。
 * 上月没有记录时 prevCount 为 0，前端据此不显示增量。
 * 首页的「本周／本月次数」也读它——**别再用封顶列表在前端过滤**（ADR 0023：累计类数字必须走全量聚合）。
 */
@Data
public class MonthSummaryVO {

    /** 月份（`YYYY-MM`），回显给前端避免时区／格式误解。 */
    private String month;

    private Integer count;

    private BigDecimal totalVolume;

    /** 本周（周一 00:00 起）训练次数——首页统计条用。 */
    private Integer weekCount;

    /** 上月训练次数（0＝上月没有记录，此时不显示环比）。 */
    private Integer prevCount;

    private BigDecimal prevTotalVolume;
}
