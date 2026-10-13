package com.example.fitapp.module.nutrition.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 食物组合的一条明细：某样食物在组合里的**固定克数**。
 * 对 food 是**弱引用**（食物删除后置空，展示靠 foodName 快照）；不存营养快照——
 * 整份热量由服务端按 food 现值现算，食物已被删的条目不参与折算。
 */
@Data
@TableName("food_combo_item")
public class FoodComboItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long comboId;

    /** 来源食物（弱引用；删除后置空）。 */
    private Long foodId;

    /** 食物名快照。 */
    private String foodName;

    /** 组合里的固定克数（ml 单位食物为毫升）。 */
    private BigDecimal grams;

    /** 组内顺序（加入一餐时按此顺序落库）。 */
    private Integer sortOrder;
}
