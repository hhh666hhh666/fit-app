package com.example.fitapp.module.plan.service;

import com.example.fitapp.common.BusinessException;
import com.example.fitapp.module.exercise.entity.Exercise;
import com.example.fitapp.module.exercise.mapper.ExerciseMapper;
import com.example.fitapp.module.plan.dto.PlanSaveDTO;
import com.example.fitapp.module.plan.entity.Plan;
import com.example.fitapp.module.plan.entity.PlanExercise;
import com.example.fitapp.module.plan.entity.PlanExerciseSet;
import com.example.fitapp.module.plan.mapper.PlanExerciseMapper;
import com.example.fitapp.module.plan.mapper.PlanExerciseSetMapper;
import com.example.fitapp.module.plan.mapper.PlanMapper;
import com.example.fitapp.module.plan.mapper.WeekScheduleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 纯单元测试（不碰库）：专测静默错误——
 * 重名计划、重复动作、已下架/他人动作混入编排、动作名快照被前端篡改、删计划不清周表引用、
 * 逐组目标漏落库、组数冗余列与行数不一致、有氧动作被塞进循环组。
 */
@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

    @Mock
    private PlanTrash planTrash;

    @Mock
    private PlanMapper planMapper;

    @Mock
    private PlanExerciseMapper planExerciseMapper;

    @Mock
    private PlanExerciseSetMapper planExerciseSetMapper;

    @Mock
    private WeekScheduleMapper weekScheduleMapper;

    @Mock
    private ExerciseMapper exerciseMapper;

    private PlanService service() {
        return new PlanService(planMapper, planExerciseMapper, planExerciseSetMapper,
                weekScheduleMapper, exerciseMapper, planTrash);
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

    private static Exercise cardio(long id) {
        Exercise e = exercise(id, null, false);
        e.setName("爬楼");
        e.setMuscle("cardio");
        e.setMetric("floors");
        return e;
    }

    private static Plan ownPlan(long id, long userId) {
        Plan p = new Plan();
        p.setId(id);
        p.setUserId(userId);
        p.setName("胸 & 三头");
        return p;
    }

    /** 旧版请求：只给组数／次数／重量三个汇总值。 */
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

    private static PlanSaveDTO.SetDTO set(Integer reps, String weight) {
        PlanSaveDTO.SetDTO s = new PlanSaveDTO.SetDTO();
        s.setReps(reps);
        s.setWeight(weight == null ? null : new BigDecimal(weight));
        return s;
    }

    /** 一组爬楼目标：层数＋（故意多传的）次数与重量，用来验服务端归一。 */
    private static PlanSaveDTO.SetDTO floorsSet(Integer floors, Integer reps, String weight) {
        PlanSaveDTO.SetDTO s = set(reps, weight);
        s.setFloors(floors);
        return s;
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
        when(planExerciseMapper.selectList(any())).thenReturn(List.of());

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
    void create_perSetTargets_persistedInOrderAndSetsCountSynced() {
        when(planMapper.selectCount(any())).thenReturn(0L);
        when(exerciseMapper.selectById(1L)).thenReturn(exercise(1L, null, false));
        when(planExerciseMapper.selectList(any())).thenReturn(List.of());

        PlanSaveDTO dto = dto("胸 & 三头", 1L);
        dto.getExercises().get(0).setTargetSets(null);
        dto.getExercises().get(0).setTargetReps(null);
        dto.getExercises().get(0).setTargetWeight(null);
        dto.getExercises().get(0).setSets(List.of(
                set(12, "60"), set(10, "65"), set(8, "65")));

        service().create(7L, dto);

        // 组数的唯一真相是行数：冗余列必须同步成 3（否则列表页 EST. VOLUME 会错）
        ArgumentCaptor<PlanExercise> peCaptor = ArgumentCaptor.forClass(PlanExercise.class);
        verify(planExerciseMapper).insert(peCaptor.capture());
        assertEquals(3, peCaptor.getValue().getTargetSets());

        ArgumentCaptor<PlanExerciseSet> setCaptor = ArgumentCaptor.forClass(PlanExerciseSet.class);
        verify(planExerciseSetMapper, times(3)).insert(setCaptor.capture());
        List<PlanExerciseSet> rows = setCaptor.getAllValues();
        assertEquals(List.of(1, 2, 3), rows.stream().map(PlanExerciseSet::getSetIndex).toList());
        assertEquals(List.of(12, 10, 8), rows.stream().map(PlanExerciseSet::getReps).toList());
        assertEquals(new BigDecimal("60"), rows.get(0).getWeight());
        assertEquals(new BigDecimal("65"), rows.get(2).getWeight());
    }

    @Test
    void create_legacyTargetSets_expandedToSameValueRows() {
        when(planMapper.selectCount(any())).thenReturn(0L);
        when(exerciseMapper.selectById(1L)).thenReturn(exercise(1L, null, false));
        when(planExerciseMapper.selectList(any())).thenReturn(List.of());

        // dto() 只给 targetSets=4 / reps=8 / weight=60，没有 sets
        service().create(7L, dto("胸 & 三头", 1L));

        ArgumentCaptor<PlanExerciseSet> setCaptor = ArgumentCaptor.forClass(PlanExerciseSet.class);
        verify(planExerciseSetMapper, times(4)).insert(setCaptor.capture());
        assertEquals(List.of(1, 2, 3, 4),
                setCaptor.getAllValues().stream().map(PlanExerciseSet::getSetIndex).toList());
        assertEquals(List.of(8, 8, 8, 8),
                setCaptor.getAllValues().stream().map(PlanExerciseSet::getReps).toList());
    }

    @Test
    void create_floorsMetric_persistsFloorsAndDropsRepsWeight() {
        when(planMapper.selectCount(any())).thenReturn(0L);
        when(exerciseMapper.selectById(5L)).thenReturn(cardio(5L));
        when(planExerciseMapper.selectList(any())).thenReturn(List.of());

        PlanSaveDTO dto = new PlanSaveDTO();
        dto.setName("爬楼日");
        PlanSaveDTO.ItemDTO item = new PlanSaveDTO.ItemDTO();
        item.setExerciseId(5L);
        // 第一组故意多传次数／重量：服务端只信层数，多传的字段必须被丢掉（ADR 0031）
        item.setSets(List.of(floorsSet(20, 12, "60"), floorsSet(20, null, null), floorsSet(15, null, null)));
        dto.setExercises(List.of(item));

        service().create(7L, dto);

        ArgumentCaptor<PlanExercise> peCaptor = ArgumentCaptor.forClass(PlanExercise.class);
        verify(planExerciseMapper).insert(peCaptor.capture());
        assertEquals(3, peCaptor.getValue().getTargetSets());
        assertNull(peCaptor.getValue().getTargetReps());
        assertNull(peCaptor.getValue().getTargetWeight());

        ArgumentCaptor<PlanExerciseSet> setCaptor = ArgumentCaptor.forClass(PlanExerciseSet.class);
        verify(planExerciseSetMapper, times(3)).insert(setCaptor.capture());
        List<PlanExerciseSet> rows = setCaptor.getAllValues();
        assertEquals(List.of(20, 20, 15), rows.stream().map(PlanExerciseSet::getFloors).toList());
        assertEquals(Arrays.asList(null, null, null), rows.stream().map(PlanExerciseSet::getReps).toList());
        assertEquals(Arrays.asList(null, null, null), rows.stream().map(PlanExerciseSet::getWeight).toList());
    }

    @Test
    void create_strengthMetric_ignoresClientFloors() {
        when(planMapper.selectCount(any())).thenReturn(0L);
        when(exerciseMapper.selectById(1L)).thenReturn(exercise(1L, null, false));
        when(planExerciseMapper.selectList(any())).thenReturn(List.of());

        PlanSaveDTO dto = new PlanSaveDTO();
        dto.setName("胸 & 三头");
        PlanSaveDTO.ItemDTO item = new PlanSaveDTO.ItemDTO();
        item.setExerciseId(1L);
        item.setSets(List.of(floorsSet(20, 12, "60")));
        dto.setExercises(List.of(item));

        service().create(7L, dto);

        ArgumentCaptor<PlanExerciseSet> setCaptor = ArgumentCaptor.forClass(PlanExerciseSet.class);
        verify(planExerciseSetMapper).insert(setCaptor.capture());
        assertNull(setCaptor.getValue().getFloors());
        assertEquals(12, setCaptor.getValue().getReps());
    }

    @Test
    void detail_floorsTarget_surfacedInSets() {
        when(planMapper.selectById(10L)).thenReturn(ownPlan(10L, 7L));
        PlanExercise pe = new PlanExercise();
        pe.setId(99L);
        pe.setPlanId(10L);
        pe.setExerciseId(5L);
        pe.setName("爬楼");
        pe.setMuscle("cardio");
        when(planExerciseMapper.selectList(any())).thenReturn(List.of(pe));
        when(exerciseMapper.selectBatchIds(any())).thenReturn(List.of(cardio(5L)));

        PlanExerciseSet row = new PlanExerciseSet();
        row.setPlanExerciseId(99L);
        row.setSetIndex(1);
        row.setFloors(18);
        when(planExerciseSetMapper.selectList(any())).thenReturn(List.of(row));

        var vo = service().detail(7L, 10L);

        assertEquals("floors", vo.getExercises().get(0).getMetric());
        assertEquals(18, vo.getExercises().get(0).getSets().get(0).getFloors());
    }

    @Test
    void create_noTargets_writesNoSetRows() {        when(planMapper.selectCount(any())).thenReturn(0L);
        when(exerciseMapper.selectById(1L)).thenReturn(exercise(1L, null, false));
        when(planExerciseMapper.selectList(any())).thenReturn(List.of());

        PlanSaveDTO dto = dto("胸 & 三头", 1L);
        dto.getExercises().get(0).setTargetSets(null);
        dto.getExercises().get(0).setTargetReps(null);
        dto.getExercises().get(0).setTargetWeight(null);
        service().create(7L, dto);

        verify(planExerciseSetMapper, never()).insert(any(PlanExerciseSet.class));
        ArgumentCaptor<PlanExercise> peCaptor = ArgumentCaptor.forClass(PlanExercise.class);
        verify(planExerciseMapper).insert(peCaptor.capture());
        assertNull(peCaptor.getValue().getTargetSets());
    }

    @Test
    void create_circuitNo_persisted() {
        when(planMapper.selectCount(any())).thenReturn(0L);
        when(exerciseMapper.selectById(1L)).thenReturn(exercise(1L, null, false));
        when(planExerciseMapper.selectList(any())).thenReturn(List.of());

        PlanSaveDTO dto = dto("推日", 1L);
        dto.getExercises().get(0).setCircuitNo(2);

        service().create(7L, dto);

        ArgumentCaptor<PlanExercise> peCaptor = ArgumentCaptor.forClass(PlanExercise.class);
        verify(planExerciseMapper).insert(peCaptor.capture());
        assertEquals(2, peCaptor.getValue().getCircuitNo());
    }

    @Test
    void create_cardioInCircuit_throws() {
        when(planMapper.selectCount(any())).thenReturn(0L);
        when(exerciseMapper.selectById(5L)).thenReturn(cardio(5L));

        PlanSaveDTO dto = dto("推日", 5L);
        dto.getExercises().get(0).setCircuitNo(1);

        assertThrows(BusinessException.class, () -> service().create(7L, dto));
        verify(planExerciseMapper, never()).insert(any(PlanExercise.class));
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
        when(planExerciseMapper.selectList(any())).thenReturn(List.of());

        service().update(7L, 10L, dto("胸 & 三头", 1L));

        // 整体替换：先清空旧编排，再按提交顺序重插
        verify(planExerciseMapper).delete(any());
        verify(planExerciseMapper).insert(any(PlanExercise.class));
        verify(planMapper).updateById(any(Plan.class));
    }

    @Test
    void update_deletesOldSetRowsBeforeReinserting() {
        when(planMapper.selectById(10L)).thenReturn(ownPlan(10L, 7L));
        when(planMapper.selectCount(any())).thenReturn(0L);
        when(exerciseMapper.selectById(1L)).thenReturn(exercise(1L, null, false));
        PlanExercise old = new PlanExercise();
        old.setId(99L);
        old.setPlanId(10L);
        old.setExerciseId(1L);
        when(planExerciseMapper.selectList(any())).thenReturn(List.of(old));

        service().update(7L, 10L, dto("胸 & 三头", 1L));

        // 子表没有外键级联：不显式删就会留下孤儿行
        verify(planExerciseSetMapper).delete(any());
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
