package com.example.fitapp.module.body.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 身体档案一屏数据：当前体重卡（含较上周与阶段最低）、基础围度、近 3 个月体重序列。
 */
@Data
public class BodyOverviewVO {

    /** 最近一条体重记录（体重卡主数据）。 */
    private Latest latest;

    /** 较上周（与 7 天前及更早的最近一条体重之差，负＝降）；无从比则为 null。 */
    private BigDecimal weekDiff;

    /** 当前体重是否为序列最低（序列 ≥2 条才判定）——「阶段最低 · PR」荧光。 */
    private Boolean isLowest;

    /** 体重序列（升序，weight 非空的记录）。 */
    private List<WeightPoint> weightSeries;

    /** 最新围度（四围任一非空的最近一条）；从未测过为 null。 */
    private Girth girth;

    @Data
    public static class Latest {

        private LocalDate date;

        private BigDecimal weight;

        private String note;
    }

    @Data
    public static class WeightPoint {

        private LocalDate date;

        private BigDecimal weight;

        public WeightPoint(LocalDate date, BigDecimal weight) {
            this.date = date;
            this.weight = weight;
        }
    }

    @Data
    public static class Girth {

        private LocalDate date;

        private BigDecimal chest;

        private BigDecimal waist;

        private BigDecimal hip;

        private BigDecimal arm;
    }
}
