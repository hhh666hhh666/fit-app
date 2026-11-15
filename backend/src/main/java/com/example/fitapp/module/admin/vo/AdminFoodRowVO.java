package com.example.fitapp.module.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 管理端预置食物列表的一行。aliases 没有为 null；imageKey 为 null＝没有图片（ADR 0056、0057）。 */
public record AdminFoodRowVO(Long id, String name, List<String> aliases, String imageKey, String category, String unit,
                             BigDecimal kcal, BigDecimal protein, BigDecimal fat, BigDecimal carb,
                             BigDecimal sodium, boolean archived, LocalDateTime updatedAt) {
}
