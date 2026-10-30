package com.example.fitapp.module.exercise.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.fitapp.module.exercise.entity.Exercise;
import com.example.fitapp.module.exercise.mapper.ExerciseMapper;
import com.example.fitapp.module.trash.entity.TrashItem;
import com.example.fitapp.module.trash.service.TrashHandler;
import com.example.fitapp.module.trash.service.TrashStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 自定义动作进出回收站（ADR 0047）。这是唯一不走快照的类型：PR 列表 INNER JOIN 动作表，
 * 物理删掉 PR 就没了。所以动作只是下架，到期或彻底删除时只摘掉恢复入口，行永远留着。
 */
@Component
@RequiredArgsConstructor
public class ExerciseTrash implements TrashHandler {

    private static final Map<String, String> MUSCLE_LABEL = Map.of(
            "chest", "胸", "back", "背", "leg", "腿", "shoulder", "肩",
            "arm", "手臂", "core", "腹部", "cardio", "有氧");

    private final ExerciseMapper exerciseMapper;
    private final TrashStore store;

    @Override
    public String type() {
        return TrashItem.EXERCISE;
    }

    void put(Long userId, Exercise e) {
        store.put(userId, type(), e.getId(), e.getName(), MUSCLE_LABEL.getOrDefault(e.getMuscle(), ""), 0L, Map.of());
    }

    @Override
    public String restore(Long userId, TrashItem item) {
        exerciseMapper.update(null, Wrappers.<Exercise>update()
                .eq("id", item.getSourceId())
                .eq("user_id", userId)
                .set("archived", false));
        return null;
    }
}
