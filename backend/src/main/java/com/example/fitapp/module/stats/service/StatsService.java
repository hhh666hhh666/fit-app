package com.example.fitapp.module.stats.service;

import com.example.fitapp.common.BusinessException;
import com.example.fitapp.common.ResultCode;
import com.example.fitapp.module.stats.vo.StatsVO;
import com.example.fitapp.module.workout.mapper.WorkoutMapper;
import com.example.fitapp.module.workout.vo.CountDurationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * 统计页：周期内次数与时长、近 4 周柱状（桶界＝周一 00:00）、各动作 PR 归档。
 * weekly 与 prs 不随 period 变化；period 只影响 count/duration 的起点。
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    private final WorkoutMapper workoutMapper;

    public StatsVO stats(Long userId, String period) {
        LocalDateTime start = switch (period == null ? "week" : period) {
            case "week" -> LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
            case "month" -> LocalDate.now().withDayOfMonth(1).atStartOfDay();
            case "year" -> LocalDate.now().withDayOfYear(1).atStartOfDay();
            default -> throw new BusinessException(ResultCode.BAD_REQUEST, "周期参数不正确");
        };

        CountDurationVO cd = workoutMapper.selectCountDuration(userId, start);

        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate[] weekStarts = new LocalDate[4];
        for (int i = 0; i < 4; i++) {
            weekStarts[i] = monday.minusWeeks(3L - i);
        }
        List<LocalDateTime> times = workoutMapper.selectCreatedAtSince(userId, weekStarts[0].atStartOfDay());
        int[] counts = new int[4];
        for (LocalDateTime t : times) {
            LocalDate d = t.toLocalDate();
            for (int i = 3; i >= 0; i--) {
                if (!d.isBefore(weekStarts[i])) {
                    counts[i]++;
                    break;
                }
            }
        }
        List<StatsVO.WeekBar> weekly = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            weekly.add(new StatsVO.WeekBar("W" + (i + 1), counts[i]));
        }

        StatsVO vo = new StatsVO();
        vo.setCount(cd.getCnt() == null ? 0 : cd.getCnt());
        vo.setDurationSec(cd.getDur() == null ? 0 : cd.getDur().intValue());
        vo.setWeekly(weekly);
        vo.setPrs(workoutMapper.selectPrArchive(userId));
        return vo;
    }
}
