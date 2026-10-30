package com.example.fitapp.module.agent.vo;

import com.example.fitapp.module.profile.vo.EnergyVO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * {@code GET /api/agent/current}：没有时间线的数据，一律是当前值（ADR 0043）。
 */
public record AgentCurrentVO(
        int schemaVersion,
        LocalDateTime generatedAt,
        String timezone,
        Profile profile,
        EnergyVO energyToday,
        DietTargets dietTargets,
        List<Plan> plans,
        List<ScheduleDay> weekSchedule,
        List<String> definitions) {

    public record Profile(String sex, LocalDate birthDate, Integer age, BigDecimal heightCm, String activityLevel) {
    }

    public record DietTargets(BigDecimal kcal, BigDecimal proteinG, BigDecimal carbG, BigDecimal fatG, Integer waterMl) {
    }

    public record Plan(Long id, String name, List<PlanExercise> exercises) {
    }

    public record PlanExercise(Long exerciseId, String name, String muscle, String metric, Integer circuitNo,
                               List<PlanSet> sets) {
    }

    public record PlanSet(Integer setIndex, Integer reps, BigDecimal weightKg, Integer floors) {
    }

    /** {@code dayOfWeek}：0＝周一 … 6＝周日；{@code state}：plan／rest／unset。 */
    public record ScheduleDay(int dayOfWeek, String state, Long planId, String planName) {
    }
}
