package com.example.fitapp.module.nutrition.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** 「某食物 ＋ 克数」一对：组合明细与「整份加入一餐」共用。 */
@Data
public class FoodAmountDTO {

    @NotNull(message = "食物不能为空")
    private Long foodId;

    @NotNull(message = "克数不能为空")
    @DecimalMin(value = "0.01", message = "克数必须大于 0")
    private BigDecimal grams;
}
