package com.example.fitapp.module.nutrition.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 食物库的一条。表结构以迁移脚本 {@code V10__nutrition.sql}、{@code V11__food_gi.sql}、
 * {@code V12__food_preset.sql}、{@code V14__food_view_count.sql}、{@code V15__food_favorite.sql} 为准；
 * 营养字段全集（45 项）见 docs/design/食物营养字段参考.md——4+1 必填（能量/蛋白/脂肪/碳水/钠），其余可空；
 * 另有扩展字段 {@code gi}（选填，不在该表七类之内，口径见该文档「本表之外的扩展字段」）。
 *
 * <p>来源分两种：{@code user_id} 为空 = 系统预置（只读，用户可「复制为自定义」），否则为所属用户自建。</p>
 */
@Data
@TableName("food")
public class Food {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户；NULL=系统预置（与 exercise 同范式）。 */
    private Long userId;

    private String name;

    /** 基准单位：g=每100g / ml=每100ml。 */
    private String unit;

    /** 分类（预置数据=《中国食物成分表》大类；用户自建为 NULL）。 */
    private String category;

    /** 浏览次数（详情页每查看一次 +1；列表按其降序，同值按名称）。预置数据为全局共享计数。 */
    private Integer viewCount;

    /** 收藏（置顶标记）：收藏者排在列表最前，同为收藏再按浏览次数降序。预置数据为全局共享标记。 */
    private Boolean favorite;

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

    // ── 升糖指数（选填；扩展字段，不在《食物营养字段参考》七类正表内）──

    /** 升糖指数 GI（葡萄糖=100 基准，无单位）。 */
    private BigDecimal gi;

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

    /** 是否系统预置（= {@code user_id} 为空）；非表字段，由服务层填充，供前端展示与交互分支。 */
    @TableField(exist = false)
    private Boolean preset;
}
