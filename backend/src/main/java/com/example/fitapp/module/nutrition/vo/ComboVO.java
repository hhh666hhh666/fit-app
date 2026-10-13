package com.example.fitapp.module.nutrition.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 食物组合视图：整份热量由 food **现值**现算（组合不存营养快照）。
 * 食物已被删的条目仍列出（靠名字快照），但 kcal / unit 为空、不计入合计。
 */
@Data
public class ComboVO {

    private Long id;

    private String name;

    /** 整份热量合计（大卡）。 */
    private BigDecimal totalKcal;

    private List<Item> items = new ArrayList<>();

    /** 组合里的一条：列表展示与「整份加入一餐」共用同一份数据。 */
    @Data
    public static class Item {

        /** 弱引用；食物已删则为 null。 */
        private Long foodId;

        private String foodName;

        private BigDecimal grams;

        /** 食物单位（g / ml）；食物已删则为 null。 */
        private String unit;

        /** 该条按克数折算的热量（大卡）；食物已删则为 null。 */
        private BigDecimal kcal;
    }
}
