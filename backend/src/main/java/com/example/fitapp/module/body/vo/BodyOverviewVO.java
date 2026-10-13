package com.example.fitapp.module.body.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 身体档案一屏数据：当前体重卡（含较上周与阶段最低）、基础围度、体重序列、围度序列。
 * 两个序列都**按日聚合**（同一天取最小，ADR 0017），所以点的 date 是日粒度。
 */
@Data
public class BodyOverviewVO {

    /** 最近一条体重记录（体重卡主数据）。 */
    private Latest latest;

    /** 较上周（与 7 天前及更早的最近一条体重之差，负＝降）；无从比则为 null。 */
    private BigDecimal weekDiff;

    /** 当前体重是否为序列最低（序列 ≥2 条才判定）——「阶段最低 · PR」荧光。 */
    private Boolean isLowest;

    /** 体重序列（按日升序，同一天取最小）。 */
    private List<WeightPoint> weightSeries;

    /** 围度序列（按日升序，各部位分别取当天最小；某部位当天没量则该字段为 null）。 */
    private List<GirthPoint> girthSeries;

    /** 最新围度（四围任一非空的最近一条）；从未测过为 null。 */
    private Girth girth;

    @Data
    public static class Latest {

        private LocalDateTime date;

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

    /** 围度序列的一个点：四个部位各取当天最小，没量的是 null。 */
    @Data
    public static class GirthPoint {

        private LocalDate date;

        private BigDecimal chest;

        private BigDecimal waist;

        private BigDecimal hip;

        private BigDecimal arm;
    }

    @Data
    public static class Girth {

        private LocalDateTime date;

        private BigDecimal chest;

        private BigDecimal waist;

        private BigDecimal hip;

        private BigDecimal arm;
    }
}
