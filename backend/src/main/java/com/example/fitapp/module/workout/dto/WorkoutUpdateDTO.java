package com.example.fitapp.module.workout.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 修改一次已入库的训练记录（详情页「编辑 → ✓」）：标题 ＋ 备注 ＋ 动作（含组次）。
 * 组次为**整体覆盖**——服务端删旧写新，总容量按组次累加、PR 重算
 * （PR 判定的历史起点**排除本记录自身**，否则改自己的数据会拿自己当历史基线）。
 * 动作条目复用 {@link WorkoutCreateDTO.BlockDTO}／{@link WorkoutCreateDTO.SetDTO}，避免两套校验口径漂开。
 */
@Data
public class WorkoutUpdateDTO {

    @NotBlank(message = "标题不能为空")
    @Size(max = 64, message = "标题过长")
    private String title;

    @Size(max = 500, message = "备注过长")
    private String note = "";

    @NotEmpty(message = "至少需要一个动作")
    @Valid
    private List<WorkoutCreateDTO.BlockDTO> blocks;
}
