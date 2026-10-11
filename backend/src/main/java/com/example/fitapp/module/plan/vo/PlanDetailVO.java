package com.example.fitapp.module.plan.vo;

import lombok.Data;

import java.util.List;

/**
 * 计划详情：名称 ＋ 按编排顺序的动作列表（含组次目标）。
 */
@Data
public class PlanDetailVO {

    private Long id;

    private String name;

    private List<Item> exercises;

    @Data
    public static class Item {

        private Long exerciseId;

        private String name;

        /** 肌群码（快照）。 */
        private String muscle;

        private Integer targetSets;

        private Integer targetReps;
    }
}
