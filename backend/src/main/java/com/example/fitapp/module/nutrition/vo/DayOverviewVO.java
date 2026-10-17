package com.example.fitapp.module.nutrition.vo;

import com.example.fitapp.module.profile.vo.EnergyVO;
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

    /**
     * 当天的每日总消耗拆解（ADR 0029）；档案不全时 `complete=false`，前端据此换成「补全档案」入口。
     * 计算在服务端（EnergyService），前端不再自己算一遍。
     */
    private EnergyVO energy;

    /**
     * 热量缺口 ＝ 总消耗 − 摄入（正数＝缺口，负数＝盈余）；算不出消耗时为 null。
     * 展示时按正数念：「缺口 320」「盈余 410」，不带正负号（ADR 0029）。
     */
    private Integer gap;

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
