package com.example.fitapp.module.nutrition.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 新建 / 编辑食物组合。明细为**整体覆盖**（编辑时全量替换）。 */
@Data
public class ComboSaveDTO {

    @NotBlank(message = "组合名不能为空")
    @Size(max = 64, message = "组合名过长")
    private String name;

    @NotEmpty(message = "组合至少需要一样食物")
    @Valid
    private List<FoodAmountDTO> items;
}
