package com.example.fitapp.module.exercise.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.fitapp.common.BusinessException;
import com.example.fitapp.module.exercise.dto.ExerciseUpsertDTO;
import com.example.fitapp.module.exercise.entity.Exercise;
import com.example.fitapp.module.exercise.mapper.ExerciseMapper;
import com.example.fitapp.module.exercise.storage.ExerciseMediaStorage;
import com.example.fitapp.module.exercise.vo.ExerciseDetailVO;
import com.example.fitapp.module.exercise.vo.ExerciseVO;
import com.example.fitapp.module.preset.service.PresetHiddenService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    /** 列表按列挑选（不读要领）要用到表元数据。 */
    @BeforeAll
    static void registerTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Exercise.class);
    }

    @Mock
    private PresetHiddenService presetHiddenService;

    @Mock
    private ExerciseTrash exerciseTrash;

    @Mock
    private ExerciseMapper exerciseMapper;

    @Mock
    private ExerciseMediaStorage mediaStorage;

    private ExerciseService service() {
        return new ExerciseService(exerciseMapper, exerciseTrash, presetHiddenService, mediaStorage);
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

    @Test
    void archive_archivedPresetUsedByUser_hidesForThisUserOnly() {
        Exercise preset = new Exercise();
        preset.setId(1L);
        preset.setUserId(null);
        preset.setName("杠铃卧推");
        preset.setArchived(true);
        when(exerciseMapper.selectById(1L)).thenReturn(preset);
        when(exerciseMapper.usedBy(7L, 1L)).thenReturn(true);

        service().archive(7L, 1L);

        // ADR 0048：只对这个用户隐藏，预置那一行不动
        verify(presetHiddenService).hide(eq(7L), eq("exercise"), eq(1L), eq("杠铃卧推"), any());
        verify(exerciseMapper, never()).updateById(any(Exercise.class));
        verify(exerciseTrash, never()).put(any(), any());
    }

    @Test
    void archive_archivedPresetNeverUsed_forbidden() {
        Exercise preset = new Exercise();
        preset.setId(1L);
        preset.setUserId(null);
        preset.setArchived(true);
        when(exerciseMapper.selectById(1L)).thenReturn(preset);
        when(exerciseMapper.usedBy(7L, 1L)).thenReturn(false);

        assertThrows(BusinessException.class, () -> service().archive(7L, 1L));
        verify(presetHiddenService, never()).hide(any(), any(), any(), any(), any());
    }

    // ── 动作详情与动图（ADR 0055）──

    private static Exercise datasetPreset() {
        Exercise e = new Exercise();
        e.setId(5L);
        e.setUserId(null);
        e.setName("杠铃卧推");
        e.setMuscle("chest");
        e.setMetric("weight_reps");
        e.setArchived(false);
        e.setNameEn("barbell bench press");
        e.setSecondary("肱三头肌、肩部");
        e.setSteps("平躺在长凳上。\n握住杠铃。\n");
        e.setMedia("0025-EIeI8Vf");
        return e;
    }

    @Test
    void detail_splitsSecondaryAndSteps_andReportsMedia() {
        when(exerciseMapper.selectOne(any())).thenReturn(datasetPreset());
        when(mediaStorage.find("0025-EIeI8Vf", ExerciseMediaStorage.Kind.GIF)).thenReturn(Path.of("/x.gif"));

        ExerciseDetailVO vo = service().detail(7L, 5L);

        assertEquals(List.of("肱三头肌", "肩部"), vo.secondary());
        assertEquals(List.of("平躺在长凳上。", "握住杠铃。"), vo.steps());
        assertTrue(vo.preset());
        assertTrue(vo.media());
    }

    @Test
    void detail_customExercise_hasEmptyListsAndNoMedia() {
        Exercise e = new Exercise();
        e.setId(9L);
        e.setUserId(7L);
        e.setName("我的动作");
        e.setMuscle("arm");
        e.setArchived(false);
        when(exerciseMapper.selectOne(any())).thenReturn(e);

        ExerciseDetailVO vo = service().detail(7L, 9L);

        assertEquals(List.of(), vo.steps());
        assertEquals(List.of(), vo.secondary());
        assertFalse(vo.media());
        assertFalse(vo.preset());
    }

    /** 看不到的动作（别人的、或已隐藏的下架预置）不能靠猜 id 取到资料和动图。 */
    @Test
    void detail_invisible_throwsNotFound() {
        when(exerciseMapper.selectOne(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service().detail(7L, 5L));
        assertThrows(BusinessException.class, () -> service().media(7L, 5L, ExerciseMediaStorage.Kind.GIF));
    }

    @Test
    void media_fileMissing_throwsNotFound() {
        when(exerciseMapper.selectOne(any())).thenReturn(datasetPreset());
        when(mediaStorage.find("0025-EIeI8Vf", ExerciseMediaStorage.Kind.THUMB)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service().media(7L, 5L, ExerciseMediaStorage.Kind.THUMB));
    }
}
