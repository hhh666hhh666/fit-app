package com.example.fitapp.module.nutrition.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 某日饮食总览：合计 / 目标 / 饮水 / 四餐分组 / 有照片的餐次。
 * 餐次分组固定四条（无条目时 items 为空数组），前端直接遍历渲染。
 */
@Data
public class DayOverviewVO {

    private String date;

    private Totals totals;

    private Targets targets;

    private Water water;

    private List<MealGroup> meals;

    /** 有照片的餐次（如 ["breakfast"]）。 */
    private List<String> photoMeals;

    @Data
    public static class Totals {

        private BigDecimal kcal;

        private BigDecimal protein;

        private BigDecimal carb;

        private BigDecimal fat;
    }

    @Data
    public static class Targets {

        private BigDecimal kcal;

        private BigDecimal protein;

        private BigDecimal carb;

        private BigDecimal fat;

        private Integer water;
    }

    @Data
    public static class Water {

        private Integer ml;

        private Integer target;
    }

    @Data
    public static class MealGroup {

        private String meal;

        private List<MealItemVO> items;
    }
}
