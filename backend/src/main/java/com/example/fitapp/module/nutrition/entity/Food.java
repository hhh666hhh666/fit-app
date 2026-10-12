package com.example.fitapp.module.nutrition.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 食物库的一条。表结构以迁移脚本 {@code V10__nutrition.sql} 为准；
 * 营养字段全集（45 项）见 docs/design/食物营养字段参考.md——4+1 必填（能量/蛋白/脂肪/碳水/钠），其余可空。
 */
@Data
@TableName("food")
public class Food {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String name;

    /** 基准单位：g=每100g / ml=每100ml。 */
    private String unit;

    // ── 主组（4+1 必填）──

    private BigDecimal kcal;

    private BigDecimal protein;

    private BigDecimal fat;

    private BigDecimal carb;

    private BigDecimal sodium;

    /** 饱和脂肪酸 g（选填）。 */
    private BigDecimal saturatedFat;

    /** 糖 g（选填）。 */
    private BigDecimal sugar;

    // ── 脂类相关（选填）──

    private BigDecimal transFat;

    private BigDecimal monoFat;

    private BigDecimal polyFat;

    private BigDecimal cholesterol;

    // ── 碳水相关（选填）──

    private BigDecimal fiber;

    private BigDecimal solubleFiber;

    private BigDecimal insolubleFiber;

    private BigDecimal starch;

    private BigDecimal lactose;

    // ── 维生素（选填）──

    private BigDecimal va;

    private BigDecimal vd;

    private BigDecimal ve;

    private BigDecimal vb1;

    private BigDecimal vb2;

    private BigDecimal vb6;

    private BigDecimal vb12;

    private BigDecimal vc;

    private BigDecimal niacin;

    private BigDecimal folate;

    private BigDecimal pantothenic;

    private BigDecimal biotin;

    // ── 矿物质（选填）──

    private BigDecimal calcium;

    private BigDecimal phosphorus;

    private BigDecimal potassium;

    private BigDecimal magnesium;

    private BigDecimal iron;

    private BigDecimal zinc;

    private BigDecimal iodine;

    private BigDecimal selenium;

    private BigDecimal copper;

    private BigDecimal manganese;

    private BigDecimal fluoride;

    private BigDecimal chromium;

    private BigDecimal molybdenum;

    // ── 理化（选填）──

    private BigDecimal water;

    private BigDecimal ash;

    private BigDecimal alcohol;

    private BigDecimal organicAcid;

    // ── 常用份（可选，成对）──

    /** 常用份标签（如「1个」）。 */
    private String portionLabel;

    /** 常用份克重（如 50）。 */
    private BigDecimal portionGrams;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
