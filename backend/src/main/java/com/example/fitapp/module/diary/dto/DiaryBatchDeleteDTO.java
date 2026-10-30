package com.example.fitapp.module.diary.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/** 批量删除日记：日记页长按多选删除。 */
@Data
public class DiaryBatchDeleteDTO {

    @NotEmpty(message = "请选择要删除的日记")
    private List<Long> ids;
}
