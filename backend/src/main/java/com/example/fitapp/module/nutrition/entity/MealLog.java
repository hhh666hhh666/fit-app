package com.example.fitapp.module.nutrition.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

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

    /** 来源食物（弱引用；删除后置空，展示靠 name 快照）。 */
    private Long foodId;

    private String foodName;

    /** 实际摄入量（克；ml 单位食物为毫升）。 */
    private BigDecimal grams;

    private BigDecimal kcalPer100;

    private BigDecimal proteinPer100;

    private BigDecimal carbPer100;

    private BigDecimal fatPer100;

    private LocalDateTime createdAt;
}
