package com.example.fitapp.module.trash.vo;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/** 恢复一条的结果。 */
@Data
public class TrashRestoreVO {
    private Long id;
    private String type;
    /** 恢复后那一行的 id，前端据此作废照片缓存。 */
    private Long sourceId;
    /** 有话要告诉主人（比如重名改了名）才有。 */
    private String note;
    /** 只有丢掉的训练才有：{abandonedAt, session}，由前端写回本机。 */
    private JsonNode session;
}
