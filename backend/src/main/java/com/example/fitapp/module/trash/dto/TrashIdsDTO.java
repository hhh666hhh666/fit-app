package com.example.fitapp.module.trash.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/** 恢复或彻底删除若干条。 */
@Data
public class TrashIdsDTO {

    @NotEmpty(message = "请选择回收站里的条目")
    private List<Long> ids;
}
