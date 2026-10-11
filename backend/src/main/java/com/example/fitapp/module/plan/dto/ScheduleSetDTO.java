package com.example.fitapp.module.plan.dto;

import lombok.Data;

/**
 * 设置周表某一天：三选一——
 * isRest=true → 休息；planId 有值 → 练该计划；两者皆空 → 清空为「未安排」。
 */
@Data
public class ScheduleSetDTO {

    private Long planId;

    private Boolean isRest;
}
