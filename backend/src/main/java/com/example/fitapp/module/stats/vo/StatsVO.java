package com.example.fitapp.module.stats.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 统计页一屏数据：周期内次数与时长、近 4 周柱状、各动作 PR 归档。
 * weekly 与 prs 不随 period 变化（周期只影响 count/duration）。
 */
@Data
public class StatsVO {

    /** 周期内训练次数。 */
    private Integer count;

    /** 周期内总训练时长（秒）。 */
    private Integer durationSec;

    /** 近 4 周训练次数（W1=三周前 … W4=本周）。 */
    private List<WeekBar> weekly;

    /** 各动作历史最佳（当前口径：单组 weight×reps 最大；按刷新时间倒序）。 */
    private List<PrItem> prs;

    @Data
    public static class WeekBar {

        private String label;

        private Integer count;

        public WeekBar(String label, Integer count) {
            this.label = label;
            this.count = count;
        }
    }

    @Data
    public static class PrItem {

        private Long exerciseId;

        /** 动作名快照（取自该 PR 组所属 workout_block）。 */
        private String name;

        private BigDecimal weight;

        private Integer reps;

        /** 该 PR 组的训练时间。 */
        private LocalDateTime performedAt;
    }
}
