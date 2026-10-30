package com.example.fitapp.module.agent.vo;

import com.example.fitapp.module.profile.vo.EnergyVO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * {@code GET /api/agent/history} 各段的行结构（ADR 0043）。整个响应是流式写出的，所以这里只有行、没有外层对象。
 */
public final class AgentHistoryRows {

    private AgentHistoryRows() {
    }

    public record Workout(Long id, String title, Long planId, LocalDateTime startedAt, LocalDate date,
                          Integer durationSec, Integer calories, BigDecimal totalVolumeKg, String note,
                          List<Block> blocks) {
    }

    public record Block(Long exerciseId, String name, String muscle, String metric, boolean cardio, List<Set> sets) {
    }

    /** 爬楼组的 {@code weightKg}／{@code reps} 恒为 null：库里那两列是占位值，不能当成绩。 */
    public record Set(Integer setIndex, BigDecimal weightKg, Integer reps, Integer floors, boolean pr,
                      Integer durationSec) {
    }

    public record Meal(Long id, LocalDate date, String meal, Long foodId, String foodName, BigDecimal grams,
                       Nutrients per100, Nutrients actual) {
    }

    public record Nutrients(BigDecimal kcal, BigDecimal proteinG, BigDecimal carbG, BigDecimal fatG) {
    }

    public record Body(Long id, LocalDateTime measuredAt, BigDecimal weightKg, BigDecimal bodyFatPercent,
                       BigDecimal chestCm, BigDecimal waistCm, BigDecimal hipCm, BigDecimal armCm, String note) {
    }

    public record Water(LocalDate date, Integer ml) {
    }

    public record Photo(Long id, LocalDate date, String meal, String mime, Integer sizeBytes, Integer thumbBytes,
                        String url, String thumbUrl) {
    }

    /**
     * 每日汇总。{@code gapKcal}＝总消耗 − 摄入：正数是缺口、负数是盈余；算不出消耗时为 null。
     * {@code mealEntryCount}／{@code mealsLogged} 用来判断这天记全了没有——没记不等于没吃。
     */
    public record Day(LocalDate date, Nutrients intake, int waterMl, int mealEntryCount, List<String> mealsLogged,
                      int workoutCount, EnergyVO energy, Integer gapKcal) {
    }

    public record Exercise(Long id, String source, String name, String muscle, String metric, boolean archived) {
    }
}
