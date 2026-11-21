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

    /** 时刻 HH:mm（ADR 0059）；老条目推断不出时为 null。 */
    private String time;

    private Long foodId;

    private String foodName;

    /** 临时条目：没挂食物库（AI 识别记的，或来源食物已删），营养只在快照里；能改名，会出现在记一餐的列表里（scope #48）。 */
    private boolean temporary;

    /** 实际摄入量（克；ml 单位食物为毫升）。 */
    private BigDecimal grams;

    private BigDecimal kcal;

    private BigDecimal protein;

    private BigDecimal carb;

    private BigDecimal fat;

    /** 钠实际摄入（mg）；条目没有钠快照（老条目、临时条目没填）时为 null。 */
    private BigDecimal sodium;
}
