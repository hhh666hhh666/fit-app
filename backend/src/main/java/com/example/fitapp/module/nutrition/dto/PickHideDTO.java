package com.example.fitapp.module.nutrition.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** 从记一餐列表里移除一种吃过的临时条目：按名字 ＋ 每 100 营养认（与列表的合并口径一致）。 */
@Data
public class PickHideDTO {

    @NotBlank(message = "名称不能为空")
    private String name;

    @NotNull(message = "热量不能为空")
    private BigDecimal kcal;

    @NotNull(message = "蛋白质不能为空")
    private BigDecimal protein;

    @NotNull(message = "脂肪不能为空")
    private BigDecimal fat;

    @NotNull(message = "碳水不能为空")
    private BigDecimal carb;

    private BigDecimal sodium;
}
