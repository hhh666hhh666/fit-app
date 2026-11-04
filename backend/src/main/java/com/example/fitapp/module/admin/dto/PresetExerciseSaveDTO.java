package com.example.fitapp.module.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 管理端新建 / 编辑预置动作（ADR 0048）。 */
@Data
public class PresetExerciseSaveDTO {

    @NotBlank(message = "动作名不能为空")
    @Size(max = 64, message = "动作名过长")
    private String name;

    @NotBlank(message = "肌群不能为空")
    private String muscle;

    /** 记录口径（ADR 0025）；缺省按重量×次数。被引用过的动作改不了。 */
    @Pattern(regexp = "weight_reps|floors", message = "记录口径只能是重量×次数或层数")
    private String metric;

    /** 动作库里的排列顺序（小的在前）；缺省排到最后。 */
    @Min(value = 0, message = "排序不能为负")
    private Integer sortOrder;
}
