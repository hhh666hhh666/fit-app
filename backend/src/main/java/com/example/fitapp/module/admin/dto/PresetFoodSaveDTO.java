package com.example.fitapp.module.admin.dto;

import com.example.fitapp.module.nutrition.dto.FoodSaveDTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理端新建 / 编辑预置食物（ADR 0048）。营养字段沿用 {@link FoodSaveDTO}，只校验 Default 组：
 * 能量、蛋白质、脂肪、碳水必填，钠可以不填；另加必填的分类。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PresetFoodSaveDTO extends FoodSaveDTO {

    @NotBlank(message = "分类不能为空")
    @Size(max = 32, message = "分类过长")
    private String category;
}
