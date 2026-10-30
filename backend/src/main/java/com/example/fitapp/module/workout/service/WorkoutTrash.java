package com.example.fitapp.module.workout.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.fitapp.module.trash.entity.TrashItem;
import com.example.fitapp.module.trash.service.TrashHandler;
import com.example.fitapp.module.trash.service.TrashStore;
import com.example.fitapp.module.workout.entity.Workout;
import com.example.fitapp.module.workout.entity.WorkoutBlock;
import com.example.fitapp.module.workout.entity.WorkoutSet;
import com.example.fitapp.module.workout.mapper.WorkoutBlockMapper;
import com.example.fitapp.module.workout.mapper.WorkoutMapper;
import com.example.fitapp.module.workout.mapper.WorkoutSetMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** 一次训练进出回收站（ADR 0047）。PR 标记原样写回：它记的是「当时是否破纪录」，不回改。 */
@Component
@RequiredArgsConstructor
public class WorkoutTrash implements TrashHandler {

    record Snapshot(Workout workout, List<WorkoutBlock> blocks, List<WorkoutSet> sets) {
    }

    private final WorkoutMapper workoutMapper;
    private final WorkoutBlockMapper blockMapper;
    private final WorkoutSetMapper setMapper;
    private final TrashStore store;

    @Override
    public String type() {
        return TrashItem.WORKOUT;
    }

    /** 存快照；删行由调用方做。 */
    void put(Long userId, Workout w) {
        List<WorkoutBlock> blocks = blockMapper.selectList(
                Wrappers.<WorkoutBlock>query().eq("workout_id", w.getId()));
        List<Long> blockIds = blocks.stream().map(WorkoutBlock::getId).toList();
        List<WorkoutSet> sets = blockIds.isEmpty()
                ? List.of()
                : setMapper.selectList(Wrappers.<WorkoutSet>query().in("block_id", blockIds));
        String summary = TrashStore.DAY.format(w.getStartedAt())
                + " · " + blocks.size() + " 个动作 · " + sets.size() + " 组";
        store.put(userId, type(), w.getId(), w.getTitle(), summary, 0L, new Snapshot(w, blocks, sets));
    }

    @Override
    public String restore(Long userId, TrashItem item) {
        Snapshot s = store.read(item, Snapshot.class);
        workoutMapper.insert(s.workout());
        s.blocks().forEach(blockMapper::insert);
        s.sets().forEach(setMapper::insert);
        return null;
    }
}
