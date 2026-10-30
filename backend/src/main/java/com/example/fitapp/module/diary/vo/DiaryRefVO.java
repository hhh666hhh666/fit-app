package com.example.fitapp.module.diary.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 记录引用卡片的数据（ADR 0044）：每次实时读取。{@code exists=false} 时只有 key 和 kind 有意义。
 * 训练只给原始值，文案由前端按记录口径（ADR 0025）拼。
 */
@Data
public class DiaryRefVO {

    /** 引用键：workout/{id}、meal/{日期}/{餐次}、day/{日期}。 */
    private String key;
    /** workout、meal 或 day。 */
    private String kind;
    private boolean exists;

    /** 记录所在的日期。 */
    private LocalDate date;

    // ── 训练 ──
    private Long workoutId;
    private String title;
    private Integer durationSec;
    private BigDecimal totalVolume;
    private Boolean hasStrength;
    private Integer totalFloors;

    // ── 饮食 ──
    /** 餐次；全天饮食为空。 */
    private String meal;
    /** 这一餐或这一天的热量合计（大卡）。 */
    private BigDecimal kcal;
    /** 条目数。 */
    private Integer itemCount;
}
