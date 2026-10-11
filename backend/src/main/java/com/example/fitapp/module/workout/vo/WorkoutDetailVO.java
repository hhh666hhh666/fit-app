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

        private String name;

        private List<SetItem> sets;
    }

    @Data
    public static class SetItem {

        private BigDecimal weight;

        private Integer reps;

        /** 提交时是否刷新该动作历史最佳（自由训练未挂动作库的组恒为 false）。 */
        private Boolean pr;
    }
}
