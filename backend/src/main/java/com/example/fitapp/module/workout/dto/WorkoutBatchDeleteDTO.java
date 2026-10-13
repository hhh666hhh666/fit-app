package com.example.fitapp.module.workout.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 批量删除训练记录（日志页长按多选删除）：一次事务删多条，任一条不属于当前用户则整批不动。
 */
@Data
public class WorkoutBatchDeleteDTO {

    @NotEmpty(message = "请选择要删除的记录")
    private List<Long> ids;
}
