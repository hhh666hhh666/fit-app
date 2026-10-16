package com.example.fitapp.module.plan.vo;

import lombok.Data;

import java.math.BigDecimal;
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

        /** 记录口径（取动作库实时值）：`floors`＝按层数录（爬楼）。见 ADR 0025。 */
        private String metric;

        private Integer targetSets;

        private Integer targetReps;

        /** 目标重量 kg；null = 未设。 */
        private BigDecimal targetWeight;
    }
}
