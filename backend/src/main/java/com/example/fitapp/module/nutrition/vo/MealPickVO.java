package com.example.fitapp.module.nutrition.vo;

import com.example.fitapp.module.nutrition.entity.Food;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 记一餐列表里的一行（scope #48）：要么是食物库里的一条食物，要么是吃过的一种临时条目。
 * 两种按同一套规则排在一起（{@code MealPickService}）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MealPickVO {

    /** {@code food} 或 {@code temp}。 */
    private String type;

    /** type=food：轻量字段的食物。 */
    private Food food;

    /** type=temp：吃过的临时条目（名字和营养一样的合成一种）。 */
    private Temp temp;

    @Data
    public static class Temp {
        private String name;
        /** 上一次记的份量，再记时默认填它。 */
        private BigDecimal grams;
        /** 每 100 的营养（和条目快照同口径）；钠可能为空。 */
        private BigDecimal kcal;
        private BigDecimal protein;
        private BigDecimal fat;
        private BigDecimal carb;
        private BigDecimal sodium;
        /** 近 30 天记过几次。 */
        private int count;
        /** 最近一次记在哪天。 */
        private LocalDate lastDate;
    }

    public static MealPickVO ofFood(Food f) {
        MealPickVO v = new MealPickVO();
        v.setType("food");
        v.setFood(f);
        return v;
    }

    public static MealPickVO ofTemp(Temp t) {
        MealPickVO v = new MealPickVO();
        v.setType("temp");
        v.setTemp(t);
        return v;
    }
}
