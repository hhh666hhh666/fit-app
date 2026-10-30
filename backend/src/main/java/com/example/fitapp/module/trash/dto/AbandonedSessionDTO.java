package com.example.fitapp.module.trash.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 「开新的」丢掉的训练：本机会话原样存，恢复时交回前端（ADR 0047）。 */
@Data
public class AbandonedSessionDTO {

    /** 丢掉的时刻（毫秒时间戳）；恢复时这段算作暂停。 */
    @NotNull(message = "缺少丢掉的时刻")
    private Long abandonedAt;

    @NotNull(message = "缺少训练数据")
    private JsonNode session;
}
