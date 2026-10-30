package com.example.fitapp.module.plan.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.fitapp.module.plan.entity.Plan;
import com.example.fitapp.module.plan.entity.PlanExercise;
import com.example.fitapp.module.plan.entity.PlanExerciseSet;
import com.example.fitapp.module.plan.entity.WeekSchedule;
import com.example.fitapp.module.plan.mapper.PlanExerciseMapper;
import com.example.fitapp.module.plan.mapper.PlanExerciseSetMapper;
import com.example.fitapp.module.plan.mapper.PlanMapper;
import com.example.fitapp.module.plan.mapper.WeekScheduleMapper;
import com.example.fitapp.module.trash.entity.TrashItem;
import com.example.fitapp.module.trash.service.TrashHandler;
import com.example.fitapp.module.trash.service.TrashStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 计划进出回收站（ADR 0047）。周安排本身不进回收站：快照只记下它占过周几，
 * 恢复时填回仍空着的格子，已经排了别的计划或「休息」的不动。
 */
@Component
@RequiredArgsConstructor
public class PlanTrash implements TrashHandler {

    record Snapshot(Plan plan, List<PlanExercise> exercises, List<PlanExerciseSet> sets, List<Integer> days) {
    }

    private static final int NAME_MAX = 32;

    private final PlanMapper planMapper;
    private final PlanExerciseMapper exerciseMapper;
    private final PlanExerciseSetMapper setMapper;
    private final WeekScheduleMapper scheduleMapper;
    private final TrashStore store;

    @Override
    public String type() {
        return TrashItem.PLAN;
    }

    /** 存快照；删行由调用方做。 */
    void put(Long userId, Plan p) {
        List<PlanExercise> exercises = exerciseMapper.selectList(
                Wrappers.<PlanExercise>query().eq("plan_id", p.getId()));
        List<Long> exerciseIds = exercises.stream().map(PlanExercise::getId).toList();
        List<PlanExerciseSet> sets = exerciseIds.isEmpty()
                ? List.of()
                : setMapper.selectList(Wrappers.<PlanExerciseSet>query().in("plan_exercise_id", exerciseIds));
        List<Integer> days = scheduleMapper.selectList(Wrappers.<WeekSchedule>query()
                        .eq("user_id", userId)
                        .eq("plan_id", p.getId()))
                .stream().map(WeekSchedule::getDayOfWeek).toList();
        store.put(userId, type(), p.getId(), p.getName(), exercises.size() + " 个动作", 0L,
                new Snapshot(p, exercises, sets, days));
    }

    @Override
    public String restore(Long userId, TrashItem item) {
        Snapshot s = store.read(item, Snapshot.class);
        Plan plan = s.plan();
        String original = plan.getName();
        plan.setName(freeName(userId, original));
        planMapper.insert(plan);
        s.exercises().forEach(exerciseMapper::insert);
        s.sets().forEach(setMapper::insert);
        for (Integer day : s.days()) {
            Long taken = scheduleMapper.selectCount(Wrappers.<WeekSchedule>query()
                    .eq("user_id", userId)
                    .eq("day_of_week", day));
            if (taken == null || taken == 0) {
                WeekSchedule cell = new WeekSchedule();
                cell.setUserId(userId);
                cell.setDayOfWeek(day);
                cell.setPlanId(plan.getId());
                cell.setIsRest(false);
                scheduleMapper.insert(cell);
            }
        }
        return original.equals(plan.getName()) ? null : "已有同名计划，改名为「" + plan.getName() + "」";
    }

    /** 同用户内计划名不重名：撞名就加「（恢复）」，还撞就接着编号。 */
    private String freeName(Long userId, String name) {
        String candidate = name;
        for (int n = 1; isTaken(userId, candidate); n++) {
            String suffix = n == 1 ? "（恢复）" : "（恢复" + n + "）";
            candidate = name.substring(0, Math.min(name.length(), NAME_MAX - suffix.length())) + suffix;
        }
        return candidate;
    }

    private boolean isTaken(Long userId, String name) {
        Long count = planMapper.selectCount(Wrappers.<Plan>query().eq("user_id", userId).eq("name", name));
        return count != null && count > 0;
    }
}
