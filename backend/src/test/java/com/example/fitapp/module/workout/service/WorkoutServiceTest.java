package com.example.fitapp.module.workout.service;

import com.example.fitapp.common.BusinessException;
import com.example.fitapp.module.plan.entity.Plan;
import com.example.fitapp.module.plan.mapper.PlanMapper;
import com.example.fitapp.module.workout.dto.WorkoutCreateDTO;
import com.example.fitapp.module.workout.entity.Workout;
import com.example.fitapp.module.workout.entity.WorkoutSet;
import com.example.fitapp.module.workout.mapper.WorkoutBlockMapper;
import com.example.fitapp.module.workout.mapper.WorkoutMapper;
import com.example.fitapp.module.workout.mapper.WorkoutSetMapper;
import com.example.fitapp.module.workout.vo.WorkoutDetailVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 纯单元测试（不碰库）：专测静默错误——
 * 总容量算错会一直错；组序号编号乱（不从 1 起、跨动作不重置）前端会显示错；
 * 来源计划引用非法时不能阻断训练入库。
 */
@ExtendWith(MockitoExtension.class)
class WorkoutServiceTest {

    @Mock
    private WorkoutMapper workoutMapper;
    @Mock
    private WorkoutBlockMapper blockMapper;
    @Mock
    private WorkoutSetMapper setMapper;
    @Mock
    private PlanMapper planMapper;

    private WorkoutService service() {
        return new WorkoutService(workoutMapper, blockMapper, setMapper, planMapper);
    }

    private WorkoutCreateDTO sampleDto() {
        WorkoutCreateDTO dto = new WorkoutCreateDTO();
        dto.setTitle(" 胸 & 三头 ");
        dto.setDurationSec(300);

        WorkoutCreateDTO.BlockDTO b1 = new WorkoutCreateDTO.BlockDTO();
        b1.setName("杠铃卧推");
        b1.setSets(List.of(set("60", 8), set("60", 7)));
        WorkoutCreateDTO.BlockDTO b2 = new WorkoutCreateDTO.BlockDTO();
        b2.setName("上斜哑铃卧推");
        b2.setSets(List.of(set("22.5", 10)));
        dto.setBlocks(List.of(b1, b2));
        return dto;
    }

    private WorkoutCreateDTO.SetDTO set(String w, int r) {
        WorkoutCreateDTO.SetDTO s = new WorkoutCreateDTO.SetDTO();
        s.setWeight(new BigDecimal(w));
        s.setReps(r);
        return s;
    }

    @Test
    void create_computesTotalVolumeFromSets() {
        service().create(1L, sampleDto());

        ArgumentCaptor<Workout> captor = ArgumentCaptor.forClass(Workout.class);
        verify(workoutMapper).insert(captor.capture());
        // 60×8 + 60×7 + 22.5×10 = 480 + 420 + 225 = 1125
        assertEquals(0, captor.getValue().getTotalVolume().compareTo(new BigDecimal("1125")));
        assertEquals("胸 & 三头", captor.getValue().getTitle());
        assertEquals(300, captor.getValue().getDurationSec());
    }

    @Test
    void create_numbersSetIndexFromOne_perBlock() {
        service().create(1L, sampleDto());

        ArgumentCaptor<WorkoutSet> captor = ArgumentCaptor.forClass(WorkoutSet.class);
        verify(setMapper, times(3)).insert(captor.capture());
        List<Integer> indexes = captor.getAllValues().stream().map(WorkoutSet::getSetIndex).toList();
        // 第一个动作两组 = 1,2；第二个动作重新从 1 计
        assertEquals(List.of(1, 2, 1), indexes);
    }

    @Test
    void create_nullDurationAndNote_defaultsApplied() {
        WorkoutCreateDTO dto = new WorkoutCreateDTO();
        dto.setTitle("自由训练");
        dto.setDurationSec(null);
        dto.setNote(null);
        WorkoutCreateDTO.BlockDTO b = new WorkoutCreateDTO.BlockDTO();
        b.setName("杠铃卧推");
        b.setSets(List.of(set("60", 8)));
        dto.setBlocks(List.of(b));

        service().create(1L, dto);

        ArgumentCaptor<Workout> captor = ArgumentCaptor.forClass(Workout.class);
        verify(workoutMapper).insert(captor.capture());
        assertEquals(0, captor.getValue().getDurationSec());
        assertEquals("", captor.getValue().getNote());
    }

    @Test
    void delete_notOwned_throwsNotFound() {
        when(workoutMapper.selectById(99L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service().delete(1L, 99L));
    }

    @Test
    void detail_returnsBlocksWithSets() {
        Workout w = new Workout();
        w.setId(1L);
        w.setUserId(1L);
        w.setTitle("胸 & 三头");
        w.setDurationSec(100);
        w.setTotalVolume(new BigDecimal("1125"));
        w.setNote("");
        when(workoutMapper.selectById(1L)).thenReturn(w);
        when(blockMapper.selectList(any())).thenReturn(List.of());

        WorkoutDetailVO vo = service().detail(1L, 1L);
        assertEquals("胸 & 三头", vo.getTitle());
        assertEquals(0, vo.getTotalVolume().compareTo(new BigDecimal("1125")));
    }

    @Test
    void create_validPlanId_saved() {
        WorkoutCreateDTO dto = sampleDto();
        dto.setPlanId(5L);
        Plan own = new Plan();
        own.setId(5L);
        own.setUserId(1L);
        when(planMapper.selectById(5L)).thenReturn(own);

        service().create(1L, dto);

        ArgumentCaptor<Workout> captor = ArgumentCaptor.forClass(Workout.class);
        verify(workoutMapper).insert(captor.capture());
        assertEquals(5L, captor.getValue().getPlanId());
    }

    @Test
    void create_invalidPlanId_ignoredButWorkoutStillSaved() {
        WorkoutCreateDTO dto = sampleDto();
        dto.setPlanId(42L); // 不存在的计划（或他人的计划）
        when(planMapper.selectById(42L)).thenReturn(null);

        service().create(1L, dto);

        // 数据优先：引用问题绝不阻断「结束训练」，plan_id 静默置 null
        ArgumentCaptor<Workout> captor = ArgumentCaptor.forClass(Workout.class);
        verify(workoutMapper).insert(captor.capture());
        assertNull(captor.getValue().getPlanId());
        assertEquals("胸 & 三头", captor.getValue().getTitle());
    }
}
