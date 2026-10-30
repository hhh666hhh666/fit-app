package com.example.fitapp.module.diary.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.fitapp.common.BusinessException;
import com.example.fitapp.module.diary.vo.DiaryRefVO;
import com.example.fitapp.module.nutrition.entity.MealLog;
import com.example.fitapp.module.nutrition.mapper.MealLogMapper;
import com.example.fitapp.module.workout.mapper.WorkoutMapper;
import com.example.fitapp.module.workout.vo.WorkoutListItemVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 纯单元测试：记录没了不报错、一餐与全天的热量口径、坏键不炸、可引用的记录只列有内容的。 */
@ExtendWith(MockitoExtension.class)
class DiaryRefServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 30);

    /** 单测里没有 Spring，要手动注册表信息，否则 Lambda 条件取不到列名。 */
    @BeforeAll
    static void registerTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), MealLog.class);
    }

    @Mock
    private WorkoutMapper workoutMapper;

    @Mock
    private MealLogMapper mealLogMapper;

    private DiaryRefService service() {
        return new DiaryRefService(workoutMapper, mealLogMapper);
    }

    private static MealLog log(String meal, String kcalPer100, String grams) {
        MealLog l = new MealLog();
        l.setLogDate(DAY);
        l.setMeal(meal);
        l.setKcalPer100(new BigDecimal(kcalPer100));
        l.setGrams(new BigDecimal(grams));
        return l;
    }

    private static WorkoutListItemVO workout(Long id) {
        WorkoutListItemVO w = new WorkoutListItemVO();
        w.setId(id);
        w.setTitle("推");
        w.setCreatedAt(LocalDateTime.of(2026, 10, 30, 23, 50));
        return w;
    }

    @Test
    void 训练删了显示不存在_不报错() {
        when(workoutMapper.selectRefItems(eq(7L), any(), any(), any())).thenReturn(List.of(workout(1L)));

        List<DiaryRefVO> refs = service().resolve(7L, List.of("workout/1", "workout/2"));

        assertTrue(refs.get(0).isExists());
        assertEquals(LocalDate.of(2026, 10, 30), refs.get(0).getDate());
        assertFalse(refs.get(1).isExists());
    }

    @Test
    void 一餐只算这一餐_全天算全部() {
        when(mealLogMapper.selectList(any())).thenReturn(List.of(
                log("lunch", "100", "200"), log("dinner", "50", "100")));

        List<DiaryRefVO> refs = service().resolve(7L, List.of("meal/2026-10-30/lunch", "day/2026-10-30"));

        assertEquals(0, new BigDecimal("200").compareTo(refs.get(0).getKcal()));
        assertEquals(1, refs.get(0).getItemCount());
        assertEquals(0, new BigDecimal("250").compareTo(refs.get(1).getKcal()));
        assertEquals(2, refs.get(1).getItemCount());
    }

    @Test
    void 这一餐条目删光了算记录已删除() {
        when(mealLogMapper.selectList(any())).thenReturn(List.of(log("dinner", "50", "100")));

        assertFalse(service().resolve(7L, List.of("meal/2026-10-30/lunch")).get(0).isExists());
    }

    @Test
    void 坏键不炸也不查库() {
        List<DiaryRefVO> refs = service().resolve(7L,
                List.of("workout/abc", "meal/不是日期/lunch", "meal/2026-10-30/夜宵", "什么/东西"));

        assertTrue(refs.stream().noneMatch(DiaryRefVO::isExists));
        verify(workoutMapper, never()).selectRefItems(any(), any(), any(), any());
    }

    @Test
    void 可引用的记录只列有内容的() {
        when(workoutMapper.selectRefItems(eq(7L), any(), any(), any())).thenReturn(List.of(workout(1L)));
        when(mealLogMapper.selectList(any())).thenReturn(List.of(log("lunch", "100", "100")));

        List<String> keys = service().candidates(7L, "2026-10-30").stream().map(DiaryRefVO::getKey).toList();

        assertEquals(List.of("workout/1", "meal/2026-10-30/lunch", "day/2026-10-30"), keys);
    }

    @Test
    void 没有饮食时不列全天() {
        when(workoutMapper.selectRefItems(eq(7L), any(), any(), any())).thenReturn(List.of());
        when(mealLogMapper.selectList(any())).thenReturn(List.of());

        assertTrue(service().candidates(7L, "2026-10-30").isEmpty());
    }

    @Test
    void 日期格式不对直接报错() {
        assertThrows(BusinessException.class, () -> service().candidates(7L, "昨天"));
    }
}
