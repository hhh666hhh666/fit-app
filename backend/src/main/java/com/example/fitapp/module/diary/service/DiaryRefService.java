package com.example.fitapp.module.diary.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.fitapp.common.BusinessException;
import com.example.fitapp.common.ResultCode;
import com.example.fitapp.module.diary.vo.DiaryRefVO;
import com.example.fitapp.module.nutrition.entity.MealLog;
import com.example.fitapp.module.nutrition.mapper.MealLogMapper;
import com.example.fitapp.module.nutrition.service.DietService;
import com.example.fitapp.module.workout.mapper.WorkoutMapper;
import com.example.fitapp.module.workout.vo.WorkoutListItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 记录引用（ADR 0044）：把正文里的引用键解析成卡片数据，并列出某天可引用的记录。
 * 数据每次实时读取、不存快照；记录没了就是 {@code exists=false}，不报错——一篇日记里
 * 有一条引用失效很正常，不该让整页出错。
 */
@Service
@RequiredArgsConstructor
public class DiaryRefService {

    /** 一篇日记里不会有这么多引用；只为防止异常请求把整张表查一遍。 */
    private static final int MAX_KEYS = 100;
    private static final List<String> MEAL_ORDER = List.of("breakfast", "lunch", "dinner", "snack");

    private final WorkoutMapper workoutMapper;
    private final MealLogMapper mealLogMapper;

    public List<DiaryRefVO> resolve(Long userId, List<String> keys) {
        List<String> distinct = keys.stream().distinct().limit(MAX_KEYS).toList();

        Set<Long> workoutIds = new HashSet<>();
        Set<LocalDate> dates = new HashSet<>();
        for (String key : distinct) {
            String[] parts = key.split("/");
            if ("workout".equals(parts[0]) && parts.length == 2) {
                Long id = parseId(parts[1]);
                if (id != null) {
                    workoutIds.add(id);
                }
            } else if (("meal".equals(parts[0]) && parts.length == 3) || ("day".equals(parts[0]) && parts.length == 2)) {
                LocalDate d = parseDate(parts[1]);
                if (d != null) {
                    dates.add(d);
                }
            }
        }

        Map<Long, WorkoutListItemVO> workouts = new HashMap<>();
        if (!workoutIds.isEmpty()) {
            for (WorkoutListItemVO w : workoutMapper.selectRefItems(userId, workoutIds, null, null)) {
                workouts.put(w.getId(), w);
            }
        }
        List<MealLog> logs = dates.isEmpty() ? List.of() : mealLogMapper.selectList(Wrappers.<MealLog>lambdaQuery()
                .eq(MealLog::getUserId, userId)
                .in(MealLog::getLogDate, dates));

        return distinct.stream().map(key -> resolveOne(key, workouts, logs)).toList();
    }

    /** 某天可引用的记录：当天的训练、有条目的餐次、有条目时的全天饮食。 */
    public List<DiaryRefVO> candidates(Long userId, String dateStr) {
        LocalDate date = parseDate(dateStr);
        if (date == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "日期格式不正确");
        }
        List<DiaryRefVO> out = new ArrayList<>();
        for (WorkoutListItemVO w : workoutMapper.selectRefItems(userId, null,
                date.atStartOfDay(), date.plusDays(1).atStartOfDay())) {
            out.add(workoutRef(w));
        }
        List<MealLog> logs = mealLogMapper.selectList(Wrappers.<MealLog>lambdaQuery()
                .eq(MealLog::getUserId, userId)
                .eq(MealLog::getLogDate, date));
        for (String meal : MEAL_ORDER) {
            DiaryRefVO r = dietRef("meal/" + date + "/" + meal, "meal", date, meal, logs);
            if (r.isExists()) {
                out.add(r);
            }
        }
        DiaryRefVO day = dietRef("day/" + date, "day", date, null, logs);
        if (day.isExists()) {
            out.add(day);
        }
        return out;
    }

    private DiaryRefVO resolveOne(String key, Map<Long, WorkoutListItemVO> workouts, List<MealLog> logs) {
        String[] parts = key.split("/");
        if ("workout".equals(parts[0]) && parts.length == 2) {
            Long id = parseId(parts[1]);
            WorkoutListItemVO w = id == null ? null : workouts.get(id);
            if (w != null) {
                return workoutRef(w);
            }
            return missing(key, "workout");
        }
        if ("meal".equals(parts[0]) && parts.length == 3 && MEAL_ORDER.contains(parts[2])) {
            LocalDate d = parseDate(parts[1]);
            return d == null ? missing(key, "meal") : dietRef(key, "meal", d, parts[2], logs);
        }
        if ("day".equals(parts[0]) && parts.length == 2) {
            LocalDate d = parseDate(parts[1]);
            return d == null ? missing(key, "day") : dietRef(key, "day", d, null, logs);
        }
        return missing(key, parts[0]);
    }

    private static DiaryRefVO workoutRef(WorkoutListItemVO w) {
        DiaryRefVO r = new DiaryRefVO();
        r.setKey("workout/" + w.getId());
        r.setKind("workout");
        r.setExists(true);
        r.setDate(w.getCreatedAt() == null ? null : w.getCreatedAt().toLocalDate());
        r.setWorkoutId(w.getId());
        r.setTitle(w.getTitle());
        r.setDurationSec(w.getDurationSec());
        r.setTotalVolume(w.getTotalVolume());
        r.setHasStrength(w.getHasStrength());
        r.setTotalFloors(w.getTotalFloors());
        return r;
    }

    private static DiaryRefVO dietRef(String key, String kind, LocalDate date, String meal, List<MealLog> logs) {
        DiaryRefVO r = new DiaryRefVO();
        r.setKey(key);
        r.setKind(kind);
        r.setDate(date);
        r.setMeal(meal);
        BigDecimal kcal = BigDecimal.ZERO;
        int count = 0;
        for (MealLog l : logs) {
            if (date.equals(l.getLogDate()) && (meal == null || meal.equals(l.getMeal()))) {
                kcal = kcal.add(DietService.actual(l.getKcalPer100(), l.getGrams()));
                count++;
            }
        }
        r.setExists(count > 0);
        r.setKcal(kcal);
        r.setItemCount(count);
        return r;
    }

    private static DiaryRefVO missing(String key, String kind) {
        DiaryRefVO r = new DiaryRefVO();
        r.setKey(key);
        r.setKind(kind);
        r.setExists(false);
        return r;
    }

    private static Long parseId(String s) {
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate parseDate(String s) {
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
