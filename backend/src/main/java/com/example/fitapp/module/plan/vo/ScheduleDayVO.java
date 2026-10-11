package com.example.fitapp.module.plan.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 周表的一格：练哪个计划 / 休息 / 未安排（两者皆空）。
 */
@Data
@AllArgsConstructor
public class ScheduleDayVO {

    /** 周几：0=周一 … 6=周日。 */
    private Integer day;

    private Long planId;

    private Boolean isRest;
}
