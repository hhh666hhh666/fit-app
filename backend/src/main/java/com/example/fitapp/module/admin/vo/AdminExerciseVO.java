package com.example.fitapp.module.admin.vo;

import java.time.LocalDateTime;

/**
 * 管理端预置动作的一行。used=true 表示有人的训练或计划引用过它：记录口径锁定（ADR 0048）。
 * nameEn、media 来自开源动作数据集（ADR 0055）：media=true 表示导入时带了动图键。
 */
public record AdminExerciseVO(Long id, String name, String muscle, String metric, Integer sortOrder,
                              boolean archived, boolean used, LocalDateTime updatedAt,
                              String nameEn, boolean media) {
}
