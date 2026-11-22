package com.example.fitapp.module.bowel.vo;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 排便统计（ADR 0061）：范围内每天大便、小便各几次；平均间隔；一天里集中在哪几个钟点；大便形态分布。
 */
@Data
public class BowelStatsVO {

    /** 统计范围的第一天与最后一天（含）。 */
    private LocalDate from;
    private LocalDate to;

    /** 范围内每一天，没记的天为 0。 */
    private List<Day> days = new ArrayList<>();

    private Kind stool = new Kind();
    private Kind urine = new Kind();

    /** 大便形态分布：下标 0–6 对应 1–7 型；没填形态的不算。 */
    private int[] bristol = new int[7];

    @Data
    public static class Day {
        private LocalDate date;
        private int stool;
        private int urine;
    }

    @Data
    public static class Kind {
        /** 范围内一共几次。 */
        private int count;
        /** 平均每天几次（按范围天数算，一位小数）。 */
        private double perDay;
        /** 相邻两次的平均间隔（小时，一位小数）；不到两次为 null。 */
        private Double avgIntervalHours;
        /** 一天 24 个钟点各几次（下标＝几点）。 */
        private int[] hours = new int[24];
    }
}
