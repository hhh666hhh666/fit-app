package com.example.fitapp.module.stats.service;

import com.example.fitapp.common.BusinessException;
import com.example.fitapp.module.stats.vo.StatsVO;
import com.example.fitapp.module.workout.mapper.WorkoutMapper;
import com.example.fitapp.module.workout.vo.CountDurationVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 纯单元测试（不碰库）：专测静默错误——
 * 周期参数乱传；近 4 周分桶错位（含「四周前不该计入」的边界）。
 */
@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock
    private WorkoutMapper workoutMapper;

    private StatsService service() {
        return new StatsService(workoutMapper);
    }

    private CountDurationVO cd(int cnt, long dur) {
        CountDurationVO vo = new CountDurationVO();
        vo.setCnt(cnt);
        vo.setDur(dur);
        return vo;
    }

    @Test
    void stats_invalidPeriod_throws() {
        assertThrows(BusinessException.class, () -> service().stats(1L, "decade"));
    }

    @Test
    void stats_weeklyBuckets_andCountDuration() {
        when(workoutMapper.selectCountDuration(eq(1L), any())).thenReturn(cd(3, 9000L));

        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        // 本周、上周（W3）、三周前（W1）、四周前（窗口外，必须不计入）
        when(workoutMapper.selectCreatedAtSince(eq(1L), any())).thenReturn(List.of(
                monday.atTime(10, 0),
                monday.minusWeeks(1).atTime(10, 0),
                monday.minusWeeks(3).atTime(10, 0),
                monday.minusWeeks(4).atTime(10, 0)
        ));
        when(workoutMapper.selectPrArchive(1L)).thenReturn(List.of());

        StatsVO vo = service().stats(1L, "week");

        assertEquals(3, vo.getCount());
        assertEquals(9000, vo.getDurationSec());
        assertEquals(4, vo.getWeekly().size());
        // W1=三周前 1 次；W2=0；W3=上周 1 次；W4=本周 1 次
        assertEquals(1, vo.getWeekly().get(0).getCount());
        assertEquals(0, vo.getWeekly().get(1).getCount());
        assertEquals(1, vo.getWeekly().get(2).getCount());
        assertEquals(1, vo.getWeekly().get(3).getCount());
        assertEquals("W1", vo.getWeekly().get(0).getLabel());
        assertEquals("W4", vo.getWeekly().get(3).getLabel());
    }

    @Test
    void stats_prsPassthrough() {
        when(workoutMapper.selectCountDuration(eq(1L), any())).thenReturn(cd(0, 0L));
        when(workoutMapper.selectCreatedAtSince(eq(1L), any())).thenReturn(List.of());

        StatsVO.PrItem pr = new StatsVO.PrItem();
        pr.setExerciseId(5L);
        pr.setName("杠铃卧推");
        pr.setWeight(new BigDecimal("70"));
        pr.setReps(3);
        pr.setPerformedAt(LocalDateTime.now());
        when(workoutMapper.selectPrArchive(1L)).thenReturn(List.of(pr));

        StatsVO vo = service().stats(1L, "month");

        assertEquals(1, vo.getPrs().size());
        assertEquals("杠铃卧推", vo.getPrs().get(0).getName());
        assertEquals(0, vo.getPrs().get(0).getWeight().compareTo(new BigDecimal("70")));
    }
}
