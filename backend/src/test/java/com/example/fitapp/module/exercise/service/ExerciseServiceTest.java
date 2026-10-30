package com.example.fitapp.module.exercise.service;

import com.example.fitapp.common.BusinessException;
import com.example.fitapp.module.exercise.dto.ExerciseUpsertDTO;
import com.example.fitapp.module.exercise.entity.Exercise;
import com.example.fitapp.module.exercise.mapper.ExerciseMapper;
import com.example.fitapp.module.exercise.vo.ExerciseVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 纯单元测试（不碰库）：专测静默错误——
 * 非法肌群混入库；预设动作被当成自定义改删；预设/自定义标记标反。
 */
@ExtendWith(MockitoExtension.class)
class ExerciseServiceTest {

    @Mock
    private ExerciseTrash exerciseTrash;

    @Mock
    private ExerciseMapper exerciseMapper;

    private ExerciseService service() {
        return new ExerciseService(exerciseMapper, exerciseTrash);
    }

    @Test
    void create_invalidMuscle_throwsBadRequest() {
        ExerciseUpsertDTO dto = new ExerciseUpsertDTO();
        dto.setName("神秘动作");
        dto.setMuscle("unknown");

        assertThrows(BusinessException.class, () -> service().create(1L, dto));
    }

    @Test
    void create_trimsNameAndKeepsUserOwnership() {
        ExerciseUpsertDTO dto = new ExerciseUpsertDTO();
        dto.setName("  保加利亚深蹲箱  ");
        dto.setMuscle("leg");

        service().create(7L, dto);

        ArgumentCaptor<Exercise> captor = ArgumentCaptor.forClass(Exercise.class);
        verify(exerciseMapper).insert(captor.capture());
        assertEquals("保加利亚深蹲箱", captor.getValue().getName());
        assertEquals(7L, captor.getValue().getUserId());
        assertFalse(Boolean.TRUE.equals(captor.getValue().getArchived()));
    }

    @Test
    void archive_presetExercise_forbidden() {
        Exercise preset = new Exercise();
        preset.setId(1L);
        preset.setUserId(null);
        preset.setArchived(false);
        when(exerciseMapper.selectById(1L)).thenReturn(preset);

        assertThrows(BusinessException.class, () -> service().archive(7L, 1L));
    }

    @Test
    void archive_ownExercise_setsArchivedFlag() {
        Exercise own = new Exercise();
        own.setId(5L);
        own.setUserId(7L);
        own.setArchived(false);
        when(exerciseMapper.selectById(5L)).thenReturn(own);

        service().archive(7L, 5L);

        ArgumentCaptor<Exercise> captor = ArgumentCaptor.forClass(Exercise.class);
        verify(exerciseMapper).updateById(captor.capture());
        assertTrue(Boolean.TRUE.equals(captor.getValue().getArchived()));
    }

    private static Exercise own(Long id) {
        Exercise e = new Exercise();
        e.setId(id);
        e.setUserId(7L);
        e.setArchived(false);
        return e;
    }

    /** 批量下架自己的动作：一次 update 统一置 archived（先逐条校验归属）。 */
    @Test
    void batchArchive_ownExercises_setsArchivedForAll() {
        when(exerciseMapper.selectById(1L)).thenReturn(own(1L));
        when(exerciseMapper.selectById(2L)).thenReturn(own(2L));

        service().batchArchive(7L, List.of(1L, 2L, 1L));

        verify(exerciseMapper).update(isNull(),
                any(com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper.class));
    }

    /** 批里混进预设动作：整批不动（不能出现「删了一半」）。 */
    @Test
    void batchArchive_presetInBatch_throwsAndUpdatesNothing() {
        Exercise preset = new Exercise();
        preset.setId(1L);
        preset.setUserId(null);
        preset.setArchived(false);
        when(exerciseMapper.selectById(1L)).thenReturn(preset);

        assertThrows(BusinessException.class, () -> service().batchArchive(7L, List.of(1L)));

        verify(exerciseMapper, never()).update(any(), any());
    }

    @Test
    void toVO_marksPresetByNullUserId() {
        Exercise preset = new Exercise();
        preset.setId(1L);
        preset.setUserId(null);
        preset.setName("杠铃卧推");
        preset.setMuscle("chest");
        Exercise custom = new Exercise();
        custom.setId(2L);
        custom.setUserId(7L);
        custom.setName("我的动作");
        custom.setMuscle("chest");
        when(exerciseMapper.selectList(any())).thenReturn(List.of(preset, custom));

        List<ExerciseVO> list = service().list(7L, null, null);
        assertTrue(list.get(0).preset());
        assertFalse(list.get(1).preset());
    }
}
