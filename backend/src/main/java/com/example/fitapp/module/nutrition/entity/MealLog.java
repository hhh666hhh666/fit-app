package com.example.fitapp.module.nutrition.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 某日某餐的一条食物记录。营养值按**当时快照**存「每 100 数值」——
 * 改库/删库不影响历史；实际摄入值 = per100 × grams ÷ 100（服务端现算）。
 */
@Data
@TableName("meal_log")
public class MealLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private LocalDate logDate;

    /** 餐次：breakfast/lunch/dinner/snack。 */
    private String meal;

    /** 时刻（几点几分吃的，ADR 0059）；老条目推断不出时为 null。 */
    private LocalTime eatenAt;

    /** 来源食物（弱引用；删除后置空，展示靠 name 快照）。 */
    private Long foodId;

    private String foodName;

    /** 实际摄入量（克；ml 单位食物为毫升）。 */
    private BigDecimal grams;

    /** 按份记时的单位快照（如「个」，ADR 0066）；按克记为 null。 */
    private String portionUnit;

    /** 按份记时一份多少 g 的快照；以后改了食物的常用份，老条目的份数不变。 */
    private BigDecimal portionGrams;

    private BigDecimal kcalPer100;

    private BigDecimal proteinPer100;

    private BigDecimal carbPer100;

    private BigDecimal fatPer100;

    /** 钠快照（每100，mg）；老条目为空（ADR 0052）。 */
    private BigDecimal sodiumPer100;

    private LocalDateTime createdAt;
}
