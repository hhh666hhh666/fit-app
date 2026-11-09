package com.example.fitapp.module.nutrition.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 记一条临时条目（ADR 0052）：AI 识别出来、用户核对过的「临时食物」，不进食物库。
 * 营养一律按每 100（g 或 ml）传——前端把「这一份」的数换算好再提交，与库里食物的快照同口径。
 * 上限与 meal_log 列宽对齐（kcal DECIMAL(7,2)、三大营养 DECIMAL(6,2)、钠 DECIMAL(8,2)）。
 */
@Data
public class MealCustomDTO {

    /** 日期（yyyy-MM-dd，服务端解析）。 */
    @NotBlank(message = "日期不能为空")
    private String date;

    @NotBlank(message = "餐次不能为空")
    @Pattern(regexp = "breakfast|lunch|dinner|snack", message = "餐次不正确")
    private String meal;

    @NotBlank(message = "名称不能为空")
    @Size(max = 64, message = "名称不能超过 64 字")
    private String name;

    /** 实际摄入量（克；按毫升计的为毫升）。 */
    @NotNull(message = "份量不能为空")
    @DecimalMin(value = "0.01", message = "份量必须大于 0")
    @DecimalMax(value = "9999.99", message = "份量过大")
    private BigDecimal grams;

    @NotNull(message = "热量不能为空")
    @DecimalMin(value = "0", message = "热量不能为负")
    @DecimalMax(value = "99999.99", message = "热量过大")
    private BigDecimal kcal;

    @NotNull(message = "蛋白质不能为空")
    @DecimalMin(value = "0", message = "蛋白质不能为负")
    @DecimalMax(value = "9999.99", message = "蛋白质过大")
    private BigDecimal protein;

    @NotNull(message = "脂肪不能为空")
    @DecimalMin(value = "0", message = "脂肪不能为负")
    @DecimalMax(value = "9999.99", message = "脂肪过大")
    private BigDecimal fat;

    @NotNull(message = "碳水不能为空")
    @DecimalMin(value = "0", message = "碳水不能为负")
    @DecimalMax(value = "9999.99", message = "碳水过大")
    private BigDecimal carb;

    /** 钠（mg，每 100）；选填——识别不出、用户也不填时为空。 */
    @DecimalMin(value = "0", message = "钠不能为负")
    @DecimalMax(value = "999999.99", message = "钠过大")
    private BigDecimal sodium;
}
