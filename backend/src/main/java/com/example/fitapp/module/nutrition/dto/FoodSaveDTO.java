package com.example.fitapp.module.nutrition.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 单条新建 / 编辑食物。字段全集与《食物营养字段参考》一一对应（另加扩展字段 GI）；
 * 4+1 必填（能量/蛋白/脂肪/碳水/钠），其余可空。
 * JSON 导入与手填共用本 DTO，故新增字段在这里加一处即两条路径同时生效。
 */
@Data
public class FoodSaveDTO {

    @NotBlank(message = "食物名不能为空")
    @Size(max = 64, message = "食物名过长")
    private String name;

    /** g=每100g / ml=每100ml；缺省按 g。 */
    @Pattern(regexp = "g|ml", message = "单位只能是 g 或 ml")
    private String unit;

    // ── 主组（4+1 必填）──

    @NotNull(message = "能量不能为空")
    @DecimalMin(value = "0", message = "能量不能为负")
    private BigDecimal kcal;

    @NotNull(message = "蛋白质不能为空")
    @DecimalMin(value = "0", message = "蛋白质不能为负")
    private BigDecimal protein;

    @NotNull(message = "脂肪不能为空")
    @DecimalMin(value = "0", message = "脂肪不能为负")
    private BigDecimal fat;

    @NotNull(message = "碳水化合物不能为空")
    @DecimalMin(value = "0", message = "碳水化合物不能为负")
    private BigDecimal carb;

    @NotNull(message = "钠不能为空")
    @DecimalMin(value = "0", message = "钠不能为负")
    private BigDecimal sodium;

    @DecimalMin(value = "0", message = "数值不能为负")
    private BigDecimal saturatedFat;

    @DecimalMin(value = "0", message = "数值不能为负")
    private BigDecimal sugar;

    // ── 以下均为选填 ──

    @DecimalMin(value = "0", message = "数值不能为负")
    private BigDecimal transFat;

    @DecimalMin(value = "0", message = "数值不能为负")
    private BigDecimal monoFat;

    @DecimalMin(value = "0", message = "数值不能为负")
    private BigDecimal polyFat;

    @DecimalMin(value = "0", message = "数值不能为负")
    private BigDecimal cholesterol;

    @DecimalMin(value = "0", message = "数值不能为负")
    private BigDecimal fiber;

    @DecimalMin(value = "0", message = "数值不能为负")
    private BigDecimal solubleFiber;

    @DecimalMin(value = "0", message = "数值不能为负")
    private BigDecimal insolubleFiber;

    @DecimalMin(value = "0", message = "数值不能为负")
    private BigDecimal starch;

    @DecimalMin(value = "0", message = "数值不能为负")
    private BigDecimal lactose;

    /** 升糖指数 GI（葡萄糖=100 基准）。只限非负，**不设上限**——真实数据表里有 >100 的食物。 */
    @DecimalMin(value = "0", message = "数值不能为负")
    private BigDecimal gi;

    @DecimalMin(value = "0", message = "数值不能为负")
    private BigDecimal va;

    @DecimalMin(value = "0", message = "数值不能为负")
    private BigDecimal vd;

    @DecimalMin(value = "0", message = "数值不能为负")
    private BigDecimal ve;

    @DecimalMin(value = "0", message = "数值不能为负")
    private BigDecimal vb1;

    @DecimalMin(value = "0", message = "数值不能为负")
    private BigDecimal vb2;

    @DecimalMin(value = "0", message = "数值不能为负")
    private BigDecimal vb6;

    @DecimalMin(value = "0", message = "数值不能为负")
    private BigDecimal vb12;

    @DecimalMin(value = "0", message = "数值不能为负")
    private BigDecimal vc;

    @DecimalMin(value = "0", message = "数值不能为负")
    private BigDecimal niacin;

    @DecimalMin(value = "0", message = "数值不能为负")
    private BigDecimal folate;

    @DecimalMin(value = "0", message = "数值不能为负")
    private BigDecimal pantothenic;

    @DecimalMin(value = "0", message = "数值不能为负")
    private BigDecimal biotin;

    @DecimalMin(value = "0", message = "数值不能为负")
    private BigDecimal calcium;

    @DecimalMin(value = "0", message = "数值不能为负")
    private BigDecimal phosphorus;

    @DecimalMin(value = "0", message = "数值不能为负")
    private BigDecimal potassium;

    @DecimalMin(value = "0", message = "数值不能为负")
    private BigDecimal magnesium;

    @DecimalMin(value = "0", message = "数值不能为负")
    private BigDecimal iron;

    @DecimalMin(value = "0", message = "数值不能为负")
    private BigDecimal zinc;

    @DecimalMin(value = "0", message = "数值不能为负")
    private BigDecimal iodine;

    @DecimalMin(value = "0", message = "数值不能为负")
    private BigDecimal selenium;

    @DecimalMin(value = "0", message = "数值不能为负")
    private BigDecimal copper;

    @DecimalMin(value = "0", message = "数值不能为负")
    private BigDecimal manganese;

    @DecimalMin(value = "0", message = "数值不能为负")
    private BigDecimal fluoride;

    @DecimalMin(value = "0", message = "数值不能为负")
    private BigDecimal chromium;

    @DecimalMin(value = "0", message = "数值不能为负")
    private BigDecimal molybdenum;

    @DecimalMin(value = "0", message = "数值不能为负")
    private BigDecimal water;

    @DecimalMin(value = "0", message = "数值不能为负")
    private BigDecimal ash;

    @DecimalMin(value = "0", message = "数值不能为负")
    private BigDecimal alcohol;

    @DecimalMin(value = "0", message = "数值不能为负")
    private BigDecimal organicAcid;

    // ── 常用份（可选成对）──

    @Size(max = 16, message = "份标签过长")
    private String portionLabel;

    @DecimalMin(value = "0.01", message = "份克重必须大于 0")
    private BigDecimal portionGrams;
}
