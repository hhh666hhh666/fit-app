package com.example.fitapp.module.plan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 新建 / 更新计划（前端「保存修改」整体提交）：名称 ＋ 按顺序的完整动作编排。
 * 更新为整体替换（先清空再插入），不做增量 diff——编排数据量小，简单可靠优先。
 */
@Data
public class PlanSaveDTO {

    @NotBlank(message = "计划名不能为空")
    @Size(max = 32, message = "计划名过长")
    private String name;

    @NotEmpty(message = "至少需要一个动作")
    @Valid
    private List<ItemDTO> exercises;

    @Data
    public static class ItemDTO {

        @NotNull(message = "动作不能为空")
        private Long exerciseId;

        /** 目标组数；null = 不设。 */
        @Min(value = 1, message = "目标组数至少为 1")
        @Max(value = 20, message = "目标组数过多")
        private Integer targetSets;

        /** 目标每组次数；null = 不设。 */
        @Min(value = 1, message = "目标次数至少为 1")
        @Max(value = 100, message = "目标次数过多")
        private Integer targetReps;
    }
}
