package com.example.fitapp.module.plan.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 计划详情：名称 ＋ 按编排顺序的动作列表（含逐组目标与循环组号）。
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

        /** 逐组目标；空 = 未设目标。**长度即组数**（ADR 0027）。 */
        private List<Set> sets;

        /** 冗余的组数（＝sets 长度），保留给旧客户端与列表页口径。 */
        private Integer targetSets;

        /** 新加一组时的默认次数（旧口径的残留字段）。 */
        private Integer targetReps;

        /** 新加一组时的默认重量 kg。 */
        private BigDecimal targetWeight;

        /** 循环组号；null = 不参与循环（ADR 0028）。 */
        private Integer circuitNo;
    }

    @Data
    public static class Set {

        /** 组序号，1 起。 */
        private Integer setIndex;

        private Integer reps;

        private BigDecimal weight;

        /** 该组目标层数：仅 `metric = floors` 的动作有值，其余 null（ADR 0031）。 */
        private Integer floors;
    }
}
