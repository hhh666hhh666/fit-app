package com.example.fitapp.module.nutrition.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 修改一条饮食条目的实际量（改食物 = 删了重加；per100 快照不动，实际值重算展示）。
 */
@Data
public class MealUpdateDTO {

    @NotNull(message = "份量不能为空")
    @DecimalMin(value = "0.01", message = "份量必须大于 0")
    @DecimalMax(value = "9999.99", message = "份量过大")
    private BigDecimal grams;
}
