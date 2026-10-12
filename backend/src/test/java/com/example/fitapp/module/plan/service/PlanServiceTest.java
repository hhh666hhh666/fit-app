package com.example.fitapp.module.plan.service;

import com.example.fitapp.common.BusinessException;
import com.example.fitapp.module.exercise.entity.Exercise;
import com.example.fitapp.module.exercise.mapper.ExerciseMapper;
import com.example.fitapp.module.plan.dto.PlanSaveDTO;
import com.example.fitapp.module.plan.entity.Plan;
import com.example.fitapp.module.plan.entity.PlanExercise;
import com.example.fitapp.module.plan.mapper.PlanExerciseMapper;
import com.example.fitapp.module.plan.mapper.PlanMapper;
import com.example.fitapp.module.plan.mapper.WeekScheduleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 纯单元测试（不碰库）：专测静默错误——
 * 重名计划、重复动作、已下架/他人动作混入编排、动作名快照被前端篡改、删计划不清周表引用。
 */
@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

    @Mock
    private PlanMapper planMapper;

    @Mock
    private PlanExerciseMapper planExerciseMapper;

    @Mock
    private WeekScheduleMapper weekScheduleMapper;

    @Mock
    private ExerciseMapper exerciseMapper;

    private PlanService service() {
        return new PlanService(planMapper, planExerciseMapper, weekScheduleMapper, exerciseMapper);
    }

    private static Exercise exercise(long id, Long ownerId, boolean archived) {
        Exercise e = new Exercise();
        e.setId(id);
        e.setUserId(ownerId);
        e.setArchived(archived);
        e.setName("杠铃卧推");
        e.setMuscle("chest");
        return e;
    }

    private static Plan ownPlan(long id, long userId) {
        Plan p = new Plan();
        p.setId(id);
        p.setUserId(userId);
        p.setName("胸 & 三头");
        return p;
    }

    private static PlanSaveDTO dto(String name, Long... exerciseIds) {
        PlanSaveDTO dto = new PlanSaveDTO();
        dto.setName(name);
        dto.setExercises(Arrays.stream(exerciseIds).map(id -> {
            PlanSaveDTO.ItemDTO item = new PlanSaveDTO.ItemDTO();
            item.setExerciseId(id);
            item.setTargetSets(4);
            item.setTargetReps(8);
            item.setTargetWeight(new BigDecimal("60"));
            return item;
        }).toList());
        return dto;
    }

    @Test
    void create_duplicateName_throws() {
        when(planMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BusinessException.class, () -> service().create(7L, dto("胸 & 三头", 1L)));
    }

    @Test
    void create_nameTrimmedAndSnapshotFromDb() {
        when(planMapper.selectCount(any())).thenReturn(0L);
        when(exerciseMapper.selectById(1L)).thenReturn(exercise(1L, null, false));
        when(planExerciseMapper.selectList(any())).thenReturn(java.util.List.of());

        service().create(7L, dto("  胸 & 三头  ", 1L));

        ArgumentCaptor<Plan> planCaptor = ArgumentCaptor.forClass(Plan.class);
        verify(planMapper).insert(planCaptor.capture());
        assertEquals("胸 & 三头", planCaptor.getValue().getName());
        assertEquals(7L, planCaptor.getValue().getUserId());

        // 名字/肌群必须来自数据库快照，而非前端输入（防篡改与漂移）
        ArgumentCaptor<PlanExercise> peCaptor = ArgumentCaptor.forClass(PlanExercise.class);
        verify(planExerciseMapper).insert(peCaptor.capture());
        assertEquals("杠铃卧推", peCaptor.getValue().getName());
        assertEquals("chest", peCaptor.getValue().getMuscle());
        assertEquals(0, peCaptor.getValue().getSortOrder());
        // 目标重量（含组次）必须整体透传落库，漏映射 = 静默丢数据
        assertEquals(new BigDecimal("60"), peCaptor.getValue().getTargetWeight());
    }

    @Test
    void create_duplicateExerciseIds_throws() {
        when(planMapper.selectCount(any())).thenReturn(0L);
        when(exerciseMapper.selectById(1L)).thenReturn(exercise(1L, null, false));

        assertThrows(BusinessException.class, () -> service().create(7L, dto("推日", 1L, 1L)));

        // 第一笔已插入的编排在事务里随异常回滚；这里只关心第二次被拦下
        verify(planExerciseMapper).insert(any(PlanExercise.class));
    }

    @Test
    void create_archivedExercise_throws() {
        when(planMapper.selectCount(any())).thenReturn(0L);
        when(exerciseMapper.selectById(2L)).thenReturn(exercise(2L, null, true));

        assertThrows(BusinessException.class, () -> service().create(7L, dto("推日", 2L)));
        verify(planExerciseMapper, never()).insert(any(PlanExercise.class));
    }

    @Test
    void create_foreignExercise_throws() {
        when(planMapper.selectCount(any())).thenReturn(0L);
        when(exerciseMapper.selectById(3L)).thenReturn(exercise(3L, 99L, false));

        assertThrows(BusinessException.class, () -> service().create(7L, dto("推日", 3L)));
        verify(planExerciseMapper, never()).insert(any(PlanExercise.class));
    }

    @Test
    void update_duplicateName_throws() {
        when(planMapper.selectById(10L)).thenReturn(ownPlan(10L, 7L));
        when(planMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BusinessException.class, () -> service().update(7L, 10L, dto("背 & 二头", 1L)));
    }

    @Test
    void update_replacesExerciseList() {
        when(planMapper.selectById(10L)).thenReturn(ownPlan(10L, 7L));
        when(planMapper.selectCount(any())).thenReturn(0L);
        when(exerciseMapper.selectById(1L)).thenReturn(exercise(1L, null, false));
        when(planExerciseMapper.selectList(any())).thenReturn(java.util.List.of());

        service().update(7L, 10L, dto("胸 & 三头", 1L));

        // 整体替换：先清空旧编排，再按提交顺序重插
        verify(planExerciseMapper).delete(any());
        verify(planExerciseMapper).insert(any(PlanExercise.class));
        verify(planMapper).updateById(any(Plan.class));
    }

    @Test
    void delete_removesExercisesScheduleRefsAndPlan() {
        when(planMapper.selectById(10L)).thenReturn(ownPlan(10L, 7L));

        service().delete(7L, 10L);

        // 三件事都不能少：编排删除、周表引用清理、计划本体删除
        verify(planExerciseMapper).delete(any());
        verify(weekScheduleMapper).delete(any());
        verify(planMapper).deleteById(10L);
    }

    @Test
    void delete_notOwn_throws() {
        when(planMapper.selectById(10L)).thenReturn(ownPlan(10L, 99L));

        assertThrows(BusinessException.class, () -> service().delete(7L, 10L));
        verify(planMapper, never()).deleteById(any(Long.class));
    }
}
