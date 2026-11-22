package com.example.fitapp.module.bowel.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 记一次或改一次（ADR 0061）。新增时 kind 必填、时刻缺省为现在；修改时整体覆盖（kind 不给就不改）。
 * 形态只对大便有效，小便给了也会被清掉。
 */
@Data
public class BowelSaveDTO {

    @Pattern(regexp = "stool|urine", message = "只能是大便或小便")
    private String kind;

    private LocalDateTime occurredAt;

    @Min(value = 1, message = "形态是 1–7 型")
    @Max(value = 7, message = "形态是 1–7 型")
    private Integer bristol;

    @Size(max = 200, message = "备注不能超过 200 字")
    private String note;
}
