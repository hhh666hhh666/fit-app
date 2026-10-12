package com.example.fitapp.module.nutrition.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 一条饮食条目（当日总览用）：营养四值为**实际摄入**（per100 × grams ÷ 100，服务端现算）。
 */
@Data
public class MealItemVO {

    private Long id;

    private String meal;

    private Long foodId;

    private String foodName;

    /** 实际摄入量（克；ml 单位食物为毫升）。 */
    private BigDecimal grams;

    private BigDecimal kcal;

    private BigDecimal protein;

    private BigDecimal carb;

    private BigDecimal fat;
}
