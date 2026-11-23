package com.example.fitapp.module.nutrition.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 边吃边数里数过的一种（ADR 0067）。有 foodId 按食物库快照；没有就是临时条目，名字和每 100 营养必填。
 * 份量＝个数 × 一个多少 g，单位与一个多少 g 一起冻结在条目上（ADR 0066）。
 */
@Data
public class MealCountItemDTO {

    private Long foodId;

    @Size(max = 64, message = "名称不能超过 64 字")
    private String name;

    @DecimalMin(value = "0", message = "热量不能为负")
    @DecimalMax(value = "99999.99", message = "热量过大")
    private BigDecimal kcal;

    @DecimalMin(value = "0", message = "蛋白质不能为负")
    @DecimalMax(value = "9999.99", message = "蛋白质过大")
    private BigDecimal protein;

    @DecimalMin(value = "0", message = "脂肪不能为负")
    @DecimalMax(value = "9999.99", message = "脂肪过大")
    private BigDecimal fat;

    @DecimalMin(value = "0", message = "碳水不能为负")
    @DecimalMax(value = "9999.99", message = "碳水过大")
    private BigDecimal carb;

    @DecimalMin(value = "0", message = "钠不能为负")
    @DecimalMax(value = "999999.99", message = "钠过大")
    private BigDecimal sodium;

    @NotNull(message = "份量不能为空")
    @DecimalMin(value = "0.01", message = "份量必须大于 0")
    @DecimalMax(value = "9999.99", message = "份量过大")
    private BigDecimal grams;

    @NotBlank(message = "单位不能为空")
    @Size(max = 8, message = "单位过长")
    private String portionUnit;

    @NotNull(message = "一个多少 g 不能为空")
    @DecimalMin(value = "0.01", message = "一个要大于 0")
    @DecimalMax(value = "99999.99", message = "一个过大")
    private BigDecimal portionGrams;
}
