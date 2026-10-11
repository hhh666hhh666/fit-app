package com.example.fitapp.module.workout.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.fitapp.common.BusinessException;
import com.example.fitapp.common.ResultCode;
import com.example.fitapp.module.plan.entity.Plan;
import com.example.fitapp.module.plan.mapper.PlanMapper;
import com.example.fitapp.module.workout.dto.WorkoutCreateDTO;
import com.example.fitapp.module.workout.entity.Workout;
import com.example.fitapp.module.workout.entity.WorkoutBlock;
import com.example.fitapp.module.workout.entity.WorkoutSet;
import com.example.fitapp.module.workout.mapper.WorkoutBlockMapper;
import com.example.fitapp.module.workout.mapper.WorkoutMapper;
import com.example.fitapp.module.workout.mapper.WorkoutSetMapper;
import com.example.fitapp.module.workout.vo.ExerciseMaxVO;
import com.example.fitapp.module.workout.vo.LastRecordVO;
import com.example.fitapp.module.workout.vo.WorkoutDetailVO;
import com.example.fitapp.module.workout.vo.WorkoutListItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 训练记录：提交（一次事务写入 workout/block/set）、列表、详情、备注、删除、上次数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkoutService {

    private final WorkoutMapper workoutMapper;
    private final WorkoutBlockMapper blockMapper;
    private final WorkoutSetMapper setMapper;
    private final PlanMapper planMapper;

    @Transactional
    public WorkoutDetailVO create(Long userId, WorkoutCreateDTO dto) {
        LocalDateTime now = LocalDateTime.now();
        BigDecimal total = dto.getBlocks().stream()
                .flatMap(b -> b.getSets().stream())
                .map(s -> s.getWeight().multiply(BigDecimal.valueOf(s.getReps())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // PR 判定的起点＝这些动作的历史最大单组容量（weight×reps）；本训练内逐组递进更新
        Set<Long> exerciseIds = dto.getBlocks().stream()
                .map(WorkoutCreateDTO.BlockDTO::getExerciseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, BigDecimal> runningMax = new HashMap<>(
                exerciseIds.isEmpty()
                        ? Map.of()
                        : workoutMapper.selectMaxSetVolume(userId, exerciseIds).stream()
                                .collect(Collectors.toMap(ExerciseMaxVO::getExerciseId, ExerciseMaxVO::getMaxVol)));

        Workout w = new Workout();
        w.setUserId(userId);
        w.setPlanId(resolvePlanId(userId, dto.getPlanId()));
        w.setTitle(dto.getTitle().trim());
        w.setStartedAt(now.minusSeconds(dto.getDurationSec() == null ? 0 : dto.getDurationSec()));
        w.setDurationSec(dto.getDurationSec() == null ? 0 : dto.getDurationSec());
        w.setTotalVolume(total);
        w.setNote(dto.getNote() == null ? "" : dto.getNote());
        workoutMapper.insert(w);

        List<WorkoutDetailVO.Block> blocks = new ArrayList<>();
        int order = 0;
        for (WorkoutCreateDTO.BlockDTO b : dto.getBlocks()) {
            WorkoutBlock block = new WorkoutBlock();
            block.setWorkoutId(w.getId());
            block.setExerciseId(b.getExerciseId());
            block.setExerciseName(b.getName().trim());
            block.setSortOrder(order++);
            blockMapper.insert(block);

            List<WorkoutDetailVO.SetItem> sets = new ArrayList<>();
            int idx = 1;
            for (WorkoutCreateDTO.SetDTO s : b.getSets()) {
                WorkoutSet ws = new WorkoutSet();
                ws.setBlockId(block.getId());
                ws.setSetIndex(idx++);
                ws.setWeight(s.getWeight());
                ws.setReps(s.getReps());
                ws.setPr(isPr(b.getExerciseId(), s, runningMax));
                setMapper.insert(ws);

                WorkoutDetailVO.SetItem item = new WorkoutDetailVO.SetItem();
                item.setWeight(s.getWeight());
                item.setReps(s.getReps());
                item.setPr(ws.getPr());
                sets.add(item);
            }

            WorkoutDetailVO.Block vb = new WorkoutDetailVO.Block();
            vb.setName(block.getExerciseName());
            vb.setSets(sets);
            blocks.add(vb);
        }
        return toDetail(w, now, blocks);
    }

    public List<WorkoutListItemVO> list(Long userId, int limit) {
        return workoutMapper.selectListItems(userId, limit);
    }

    public WorkoutDetailVO detail(Long userId, Long id) {
        Workout w = requireOwn(userId, id);
        List<WorkoutBlock> blocks = blockMapper.selectList(
                Wrappers.<WorkoutBlock>lambdaQuery()
                        .eq(WorkoutBlock::getWorkoutId, id)
                        .orderByAsc(WorkoutBlock::getSortOrder)
                        .orderByAsc(WorkoutBlock::getId));
        List<WorkoutDetailVO.Block> vbs = new ArrayList<>();
        for (WorkoutBlock b : blocks) {
            List<WorkoutSet> sets = setMapper.selectList(
                    Wrappers.<WorkoutSet>lambdaQuery()
                            .eq(WorkoutSet::getBlockId, b.getId())
                            .orderByAsc(WorkoutSet::getSetIndex));
            WorkoutDetailVO.Block vb = new WorkoutDetailVO.Block();
            vb.setName(b.getExerciseName());
            vb.setSets(sets.stream().map(s -> {
                WorkoutDetailVO.SetItem item = new WorkoutDetailVO.SetItem();
                item.setWeight(s.getWeight());
                item.setReps(s.getReps());
                item.setPr(Boolean.TRUE.equals(s.getPr()));
                return item;
            }).toList());
            vbs.add(vb);
        }
        return toDetail(w, w.getCreatedAt(), vbs);
    }

    public void updateNote(Long userId, Long id, String note) {
        Workout w = requireOwn(userId, id);
        w.setNote(note == null ? "" : note);
        workoutMapper.updateById(w);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        requireOwn(userId, id);
        workoutMapper.deleteSetsByWorkoutId(id);
        blockMapper.delete(Wrappers.<WorkoutBlock>lambdaQuery().eq(WorkoutBlock::getWorkoutId, id));
        workoutMapper.deleteById(id);
    }

    /** 某动作的「上次数据」；没有记录时返回 null。 */
    public LastRecordVO lastRecord(Long userId, Long exerciseId) {
        return workoutMapper.selectLastRecord(userId, exerciseId);
    }

    /**
     * PR 口径（scope #7）：该动作历史「最重一组」（weight×reps）。
     * 严格大于才刷新（并列不算新 PR）；无历史即当前最佳＝PR；未挂动作库的组不判定。
     */
    private boolean isPr(Long exerciseId, WorkoutCreateDTO.SetDTO s, Map<Long, BigDecimal> runningMax) {
        if (exerciseId == null) {
            return false;
        }
        BigDecimal vol = s.getWeight().multiply(BigDecimal.valueOf(s.getReps()));
        BigDecimal max = runningMax.get(exerciseId);
        if (max == null || vol.compareTo(max) > 0) {
            runningMax.put(exerciseId, vol);
            return true;
        }
        return false;
    }

    private Workout requireOwn(Long userId, Long id) {
        Workout w = workoutMapper.selectById(id);
        if (w == null || !userId.equals(w.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "训练记录不存在");
        }
        return w;
    }

    /**
     * 来源计划弱引用：存在且属于当前用户才存，否则记警告并忽略。
     * 刻意不抛异常——训练数据优先，「结束训练」绝不因引用问题白录（前端 bug 也不能丢数据）。
     */
    private Long resolvePlanId(Long userId, Long planId) {
        if (planId == null) {
            return null;
        }
        Plan p = planMapper.selectById(planId);
        if (p != null && userId.equals(p.getUserId())) {
            return planId;
        }
        log.warn("忽略非法的来源计划 planId={}（user={}）", planId, userId);
        return null;
    }

    private WorkoutDetailVO toDetail(Workout w, LocalDateTime createdAt, List<WorkoutDetailVO.Block> blocks) {
        WorkoutDetailVO vo = new WorkoutDetailVO();
        vo.setId(w.getId());
        vo.setTitle(w.getTitle());
        vo.setCreatedAt(createdAt == null ? LocalDateTime.now() : createdAt);
        vo.setDurationSec(w.getDurationSec());
        vo.setTotalVolume(w.getTotalVolume());
        vo.setNote(w.getNote());
        vo.setBlocks(blocks);
        return vo;
    }
}
