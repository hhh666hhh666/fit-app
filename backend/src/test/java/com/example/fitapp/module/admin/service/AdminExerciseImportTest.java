package com.example.fitapp.module.admin.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.fitapp.common.BusinessException;
import com.example.fitapp.module.admin.vo.ImportPreviewVO;
import com.example.fitapp.module.exercise.entity.Exercise;
import com.example.fitapp.module.exercise.mapper.ExerciseMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 纯单元测试：导入是一千多条一次进库的批量写，静默错误最贵——
 * 老预置被重复建一份、被人练过的老预置肌群被改、同一编号对上两行、重名混进库。
 */
@ExtendWith(MockitoExtension.class)
class AdminExerciseImportTest {

    @BeforeAll
    static void registerTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Exercise.class);
    }

    @Mock
    private ExerciseMapper exerciseMapper;

    private AdminExerciseImport importer() {
        return new AdminExerciseImport(exerciseMapper, new ObjectMapper());
    }

    private static Exercise preset(long id, String name, String muscle, String sourceId) {
        Exercise e = new Exercise();
        e.setId(id);
        e.setName(name);
        e.setMuscle(muscle);
        e.setMetric("weight_reps");
        e.setSortOrder((int) id);
        e.setArchived(false);
        e.setSourceId(sourceId);
        return e;
    }

    private static String item(String sourceId, String name, String muscle) {
        return """
                {"sourceId":"%s","name":"%s","nameEn":"x","muscle":"%s","equipment":"杠铃","target":"胸肌",
                 "secondary":["肱三头肌","肩部"],"steps":["第一步","第二步"],"media":"%s-AbCdE"}"""
                .formatted(sourceId, name, muscle, sourceId);
    }

    @Test
    void plan_sameNameWithoutSource_isAdoptedNotDuplicated() {
        // 老预置「硬拉」被人练过，肌群是背；数据集把它归到腿
        when(exerciseMapper.selectList(any())).thenReturn(List.of(preset(19, "硬拉", "back", null)));

        AdminExerciseImport.Plan plan = importer().plan("[" + item("0032", "硬拉", "leg") + "]");

        assertEquals(1, plan.items().size());
        assertTrue(plan.items().get(0).adopt());
        assertEquals(19L, plan.items().get(0).target().getId());
        ImportPreviewVO vo = importer().preview("[" + item("0032", "硬拉", "leg") + "]");
        assertEquals(0, vo.create());
        assertEquals(1, vo.update());
    }

    @Test
    void plan_sameSourceId_updatesThatRow_evenIfRenamed() {
        when(exerciseMapper.selectList(any())).thenReturn(List.of(preset(100, "杠铃卧推", "chest", "0025")));

        AdminExerciseImport.Plan plan = importer().plan("[" + item("0025", "平板杠铃卧推", "chest") + "]");

        assertEquals(1, plan.items().size());
        assertFalse(plan.items().get(0).adopt());
        assertEquals(100L, plan.items().get(0).target().getId());
    }

    @Test
    void plan_rejectsDuplicatesAndConflicts() {
        when(exerciseMapper.selectList(any())).thenReturn(List.of(
                preset(1, "深蹲", "leg", "0043"),
                preset(2, "前蹲", "leg", "0042")));
        String text = "[" + String.join(",",
                item("0100", "新动作", "chest"),
                item("0100", "新动作二", "chest"),          // 编号重复
                item("0101", "新动作", "chest"),            // 名字重复
                item("0042", "深蹲", "leg"),                // 改名撞上另一行
                item("0102", "怪动作", "nope"),             // 肌群不对
                "{\"id\":999,\"name\":\"x\",\"muscle\":\"leg\"}") + "]"; // id 不是预置

        AdminExerciseImport.Plan plan = importer().plan(text);

        assertEquals(1, plan.items().size());
        assertEquals("新动作", plan.items().get(0).item().name());
        assertEquals(List.of(2, 3, 4, 5, 6),
                plan.invalid().stream().map(ImportPreviewVO.Invalid::index).toList());
    }

    @Test
    void plan_badJson_throws() {
        assertThrows(BusinessException.class, () -> importer().plan("not json"));
    }

    @Test
    void importText_newRow_appendsAfterLastSortOrder_withJoinedFields() {
        when(exerciseMapper.selectList(any())).thenReturn(List.of(preset(1, "深蹲", "leg", null)));
        Exercise last = new Exercise();
        last.setSortOrder(60);
        when(exerciseMapper.selectOne(any())).thenReturn(last);

        ImportPreviewVO vo = importer().importText("[" + item("0025", "杠铃卧推", "chest") + "]");

        assertEquals(1, vo.create());
        ArgumentCaptor<Exercise> captor = ArgumentCaptor.forClass(Exercise.class);
        verify(exerciseMapper, times(1)).insert(captor.capture());
        Exercise e = captor.getValue();
        assertEquals(61, e.getSortOrder());
        assertEquals("weight_reps", e.getMetric());
        assertEquals("肱三头肌、肩部", e.getSecondary());
        assertEquals("第一步\n第二步", e.getSteps());
        assertEquals("0025-AbCdE", e.getMedia());
        assertEquals(null, e.getUserId());
    }
}
