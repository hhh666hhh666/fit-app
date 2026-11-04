package com.example.fitapp.module.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 管理端预置食物列表的一行。 */
public record AdminFoodRowVO(Long id, String name, String category, String unit,
                             BigDecimal kcal, BigDecimal protein, BigDecimal fat, BigDecimal carb,
                             BigDecimal sodium, boolean archived, LocalDateTime updatedAt) {
}
