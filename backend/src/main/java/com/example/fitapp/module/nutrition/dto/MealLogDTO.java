package com.example.fitapp.module.nutrition.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 新增一条饮食条目：从食物库选食物 + 实际摄入量。营养按食物当时值快照。
 */
@Data
public class MealLogDTO {

    /** 日期（yyyy-MM-dd，服务端解析）。 */
    @NotBlank(message = "日期不能为空")
    private String date;

    @NotBlank(message = "餐次不能为空")
    @Pattern(regexp = "breakfast|lunch|dinner|snack", message = "餐次不正确")
    private String meal;

    @NotNull(message = "食物不能为空")
    private Long foodId;

    /** 实际摄入量（克；ml 单位食物为毫升）。 */
    @NotNull(message = "份量不能为空")
    @DecimalMin(value = "0.01", message = "份量必须大于 0")
    @DecimalMax(value = "9999.99", message = "份量过大")
    private BigDecimal grams;
}
