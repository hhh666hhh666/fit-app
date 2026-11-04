package com.example.fitapp.module.admin.vo;

import java.time.LocalDateTime;

/** 管理端预置动作的一行。used=true 表示有人的训练或计划引用过它：记录口径锁定（ADR 0048）。 */
public record AdminExerciseVO(Long id, String name, String muscle, String metric, Integer sortOrder,
                              boolean archived, boolean used, LocalDateTime updatedAt) {
}
