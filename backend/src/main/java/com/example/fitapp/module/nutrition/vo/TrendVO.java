package com.example.fitapp.module.nutrition.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 近 N 天趋势：逐日五指标（热量/蛋白/碳水/脂肪为摄入；饮水为 ml）。
 * 缺日补零。
 */
@Data
public class TrendVO {

    private List<Point> days;

    @Data
    public static class Point {

        private String date;

        private BigDecimal kcal;

        private BigDecimal protein;

        private BigDecimal carb;

        private BigDecimal fat;

        private Integer water;
    }
}
