package com.example.fitapp.module.workout.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 一次训练的明细（提交后的返回 / 详情查询）：汇总数据 ＋ 动作与组次。
 */
@Data
public class WorkoutDetailVO {

    private Long id;

    private String title;

    private LocalDateTime createdAt;

    private Integer durationSec;

    private BigDecimal totalVolume;

    private String note;

    private List<Block> blocks;

    @Data
    public static class Block {

        /** 关联动作库的 id（未挂库的临场动作为 null）；详情页编辑保存时回传，用于重算 PR。 */
        private Long exerciseId;

        private String name;

        /** 记录口径：`floors`＝按层数录（爬楼）；`weight_reps` 或 null＝重量×次数。见 ADR 0025。 */
        private String metric;

        /**
         * 是否是有氧动作（肌群码 `cardio`）——有氧不参与容量与 PR（ADR 0023／0024），
         * 前端据此把容量位显示成「—」；未挂动作库的块为 false。
         */
        private Boolean cardio;

        private List<SetItem> sets;
    }

    @Data
    public static class SetItem {

        private BigDecimal weight;

        private Integer reps;

        /** 层数（`metric=floors` 的组有值，其余 null）；这类组的 weight／reps 是占位值 0／1。 */
        private Integer floors;

        /** 提交时是否刷新该动作历史最佳（自由训练未挂动作库的组恒为 false）。 */
        private Boolean pr;

        /** 该组净用时（秒，剔除暂停段）；null ＝ 无用时（历史数据与编辑时新增的组）。 */
        private Integer durationSec;
    }
}
