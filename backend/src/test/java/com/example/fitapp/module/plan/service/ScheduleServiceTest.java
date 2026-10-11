package com.example.fitapp.module.plan.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.fitapp.common.BusinessException;
import com.example.fitapp.module.plan.dto.ScheduleSetDTO;
import com.example.fitapp.module.plan.entity.Plan;
import com.example.fitapp.module.plan.entity.WeekSchedule;
import com.example.fitapp.module.plan.mapper.PlanMapper;
import com.example.fitapp.module.plan.mapper.WeekScheduleMapper;
import com.example.fitapp.module.plan.vo.ScheduleDayVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 纯单元测试（不碰库）：专测静默错误——
 * 「计划 → 休息」切换必须强写 plan_id=null（updateById 会被 not_null 策略静默跳过）；
 * 清空必须删行；引用他人计划必须拒绝；周表读取必须补齐 7 天。
 */
@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    /**
     * 纯单测没有 MyBatis 启动扫描：lambdaUpdate().set(...) 会立即解析列名，
     * 需要 TableInfo 缓存（不像懒序列化的 eq 段在 mock 场景不触发解析）。
     * 手工注册一次，等价于启动时扫到该实体。
     */
    @BeforeAll
    static void registerTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), WeekSchedule.class);
    }

    @Mock
    private WeekScheduleMapper weekScheduleMapper;

    @Mock
    private PlanMapper planMapper;

    private ScheduleService service() {
        return new ScheduleService(weekScheduleMapper, planMapper);
    }

    private static ScheduleSetDTO set(Long planId, Boolean isRest) {
        ScheduleSetDTO dto = new ScheduleSetDTO();
        dto.setPlanId(planId);
        dto.setIsRest(isRest);
        return dto;
    }

    @Test
    void set_invalidDay_throws() {
        assertThrows(BusinessException.class, () -> service().set(7L, 7, set(null, true)));
    }

    @Test
    void set_clear_deletesRow() {
        service().set(7L, 3, set(null, false));

        verify(weekScheduleMapper).delete(any());
        verify(weekScheduleMapper, never()).insert(any(WeekSchedule.class));
        verify(weekScheduleMapper, never()).update(any(), any());
    }

    @Test
    void set_foreignPlan_throws() {
        Plan other = new Plan();
        other.setId(5L);
        other.setUserId(99L);
        when(planMapper.selectById(5L)).thenReturn(other);

        assertThrows(BusinessException.class, () -> service().set(7L, 3, set(5L, false)));
    }

    @Test
    void set_restOnExistingPlanDay_strongWritesNullPlanId() {
        WeekSchedule existing = new WeekSchedule();
        existing.setId(1L);
        existing.setUserId(7L);
        existing.setDayOfWeek(3);
        existing.setPlanId(5L);
        existing.setIsRest(false);
        when(weekScheduleMapper.selectOne(any())).thenReturn(existing);

        service().set(7L, 3, set(null, true));

        // 关键断言：走 update(entity=null, wrapper) 强写路径。
        // 若退化成 updateById，plan_id=null 会被全局 not_null 策略静默跳过（脏数据）。
        verify(weekScheduleMapper).update(ArgumentMatchers.<WeekSchedule>isNull(), any());
    }

    @Test
    void set_planOnEmptyDay_insertsRow() {
        when(weekScheduleMapper.selectOne(any())).thenReturn(null);
        Plan own = new Plan();
        own.setId(5L);
        own.setUserId(7L);
        when(planMapper.selectById(5L)).thenReturn(own);

        service().set(7L, 3, set(5L, false));

        var captor = org.mockito.ArgumentCaptor.forClass(WeekSchedule.class);
        verify(weekScheduleMapper).insert(captor.capture());
        assertEquals(7L, captor.getValue().getUserId());
        assertEquals(3, captor.getValue().getDayOfWeek());
        assertEquals(5L, captor.getValue().getPlanId());
        assertFalse(Boolean.TRUE.equals(captor.getValue().getIsRest()));
    }

    @Test
    void week_fillsMissingDaysAsUnarranged() {
        WeekSchedule rest = new WeekSchedule();
        rest.setId(1L);
        rest.setUserId(7L);
        rest.setDayOfWeek(3);
        rest.setPlanId(null);
        rest.setIsRest(true);
        when(weekScheduleMapper.selectList(any())).thenReturn(List.of(rest));

        List<ScheduleDayVO> week = service().week(7L);

        assertEquals(7, week.size());
        assertTrue(week.get(3).getIsRest());
        assertNull(week.get(3).getPlanId());
        assertFalse(week.get(0).getIsRest());
        assertNull(week.get(0).getPlanId());
    }
}
