package com.example.fitapp.module.admin.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 管理端批量操作（下架、上架）选中的 id。 */
@Data
public class AdminIdsDTO {

    @NotEmpty(message = "请至少选一条")
    @Size(max = 2000, message = "一次选得太多了")
    private List<Long> ids;
}
