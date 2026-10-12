package com.example.fitapp.module.nutrition.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * JSON 文本导入（复制粘贴整段）：服务端解析为食物数组，逐条校验并入库。
 */
@Data
public class FoodImportDTO {

    @NotBlank(message = "导入内容不能为空")
    private String text;
}
