package com.example.fitapp.module.nutrition.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 趋势聚合的中间行：某日的营养合计（餐次条目按 per100 × grams ÷ 100 累加）。
 */
@Data
public class DailyNutritionRow {

    private LocalDate logDate;

    private BigDecimal kcal;

    private BigDecimal protein;

    private BigDecimal carb;

    private BigDecimal fat;
}
