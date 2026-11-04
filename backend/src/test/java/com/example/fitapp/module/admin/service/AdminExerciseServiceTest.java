package com.example.fitapp.module.admin.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.fitapp.common.BusinessException;
import com.example.fitapp.module.admin.dto.PresetExerciseSaveDTO;
import com.example.fitapp.module.exercise.entity.Exercise;
import com.example.fitapp.module.exercise.mapper.ExerciseMapper;
import com.example.fitapp.module.preset.service.PresetHiddenService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 纯单元测试：被引用过的动作改了记录口径，历史组次的含义就变了（ADR 0025 / 0048）。 */
@ExtendWith(MockitoExtension.class)
class AdminExerciseServiceTest {

    @BeforeAll
    static void registerTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Exercise.class);
    }

    @Mock
    private ExerciseMapper exerciseMapper;

    @Mock
    private PresetHiddenService presetHiddenService;

    private AdminExerciseService service() {
        return new AdminExerciseService(exerciseMapper, presetHiddenService);
    }

    private static Exercise preset() {
        Exercise e = new Exercise();
        e.setId(1L);
        e.setUserId(null);
        e.setName("爬楼");
        e.setMuscle("cardio");
        e.setMetric("floors");
        e.setSortOrder(3);
        e.setArchived(false);
        return e;
    }

    private static PresetExerciseSaveDTO dto(String metric) {
        PresetExerciseSaveDTO d = new PresetExerciseSaveDTO();
        d.setName("爬楼");
        d.setMuscle("cardio");
        d.setMetric(metric);
        return d;
    }

    @Test
    void update_usedExerciseMetricChange_rejected() {
        when(exerciseMapper.selectById(1L)).thenReturn(preset());
        when(exerciseMapper.usedByAnyone(1L)).thenReturn(true);

        assertThrows(BusinessException.class, () -> service().update(1L, dto("weight_reps")));
        verify(exerciseMapper, never()).update(any(), any());
    }

    @Test
    void update_usedExerciseSameMetric_allowed() {
        when(exerciseMapper.selectById(1L)).thenReturn(preset());
        when(exerciseMapper.usedByAnyone(1L)).thenReturn(true);

        service().update(1L, dto(null));

        verify(exerciseMapper).update(any(), any());
    }

    @Test
    void update_userExercise_notFound() {
        Exercise own = preset();
        own.setUserId(7L);
        when(exerciseMapper.selectById(1L)).thenReturn(own);

        assertThrows(BusinessException.class, () -> service().update(1L, dto(null)));
    }

    @Test
    void unarchive_clearsEveryUsersHiding() {
        when(exerciseMapper.selectCount(any())).thenReturn(1L);

        service().unarchive(List.of(1L));

        verify(presetHiddenService).clearAll(eq("exercise"), eq(List.of(1L)));
    }
}
