package com.example.fitapp.module.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 管理端粘贴或上传的预置食物 / 预置动作 JSON（格式同各自的导出，ADR 0048、ADR 0055）。 */
@Data
public class AdminImportDTO {

    @NotBlank(message = "导入内容不能为空")
    private String text;
}
