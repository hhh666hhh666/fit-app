package com.example.fitapp.module.body.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 批量删除身体档案记录（身体档案页长按多选删除）：一次事务删多条，任一条不属于当前用户则整批不动。
 */
@Data
public class BodyBatchDeleteDTO {

    @NotEmpty(message = "请选择要删除的记录")
    private List<Long> ids;
}
