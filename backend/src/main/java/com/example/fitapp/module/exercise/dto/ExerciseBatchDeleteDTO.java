package com.example.fitapp.module.exercise.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/** 批量下架当前用户的自定义动作。 */
@Data
public class ExerciseBatchDeleteDTO {

    @NotEmpty(message = "请选择要删除的动作")
    private List<Long> ids;
}
