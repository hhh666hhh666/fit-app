package com.example.fitapp.module.plan.service;

import com.example.fitapp.module.plan.entity.WeekSchedule;
import com.example.fitapp.module.plan.mapper.PlanExerciseMapper;
import com.example.fitapp.module.plan.mapper.PlanExerciseSetMapper;
import com.example.fitapp.module.plan.mapper.PlanMapper;
import com.example.fitapp.module.plan.mapper.WeekScheduleMapper;
import com.example.fitapp.module.trash.entity.TrashItem;
import com.example.fitapp.module.trash.mapper.TrashItemMapper;
import com.example.fitapp.module.trash.service.TrashStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 周安排本身不进回收站：恢复计划时只填回仍空着的格子（ADR 0047）。 */
@ExtendWith(MockitoExtension.class)
class PlanTrashTest {

    @Mock
    private PlanMapper planMapper;

    @Mock
    private PlanExerciseMapper exerciseMapper;

    @Mock
    private PlanExerciseSetMapper setMapper;

    @Mock
    private WeekScheduleMapper scheduleMapper;

    @Mock
    private TrashItemMapper trashItemMapper;

    @Test
    void 被占的格子不动_空格子填回() {
        when(planMapper.selectCount(any())).thenReturn(0L);
        // 周一已经排了别的，周四还空着
        when(scheduleMapper.selectCount(any())).thenReturn(1L, 0L);
        TrashItem i = new TrashItem();
        i.setSnapshot("""
                {"plan":{"id":6,"userId":7,"name":"推胸"},"exercises":[],"sets":[],"days":[0,3]}
                """);

        PlanTrash trash = new PlanTrash(planMapper, exerciseMapper, setMapper, scheduleMapper,
                new TrashStore(trashItemMapper, new ObjectMapper()));

        assertNull(trash.restore(7L, i));

        ArgumentCaptor<WeekSchedule> cell = ArgumentCaptor.forClass(WeekSchedule.class);
        verify(scheduleMapper, times(1)).insert(cell.capture());
        assertEquals(3, cell.getValue().getDayOfWeek());
        assertEquals(6L, cell.getValue().getPlanId());
    }
}
