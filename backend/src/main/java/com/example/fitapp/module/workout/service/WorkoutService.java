package com.example.fitapp.module.workout.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.fitapp.common.BusinessException;
import com.example.fitapp.common.ResultCode;
import com.example.fitapp.module.plan.entity.Plan;
import com.example.fitapp.module.plan.mapper.PlanMapper;
import com.example.fitapp.module.workout.dto.WorkoutCreateDTO;
import com.example.fitapp.module.workout.dto.WorkoutUpdateDTO;
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
        BigDecimal total = sumVolume(dto.getBlocks());

        // PR 判定的起点＝这些动作的历史最大单组容量（weight×reps）；本训练内逐组递进更新
        Map<Long, BigDecimal> runningMax = historyMax(userId, dto.getBlocks(), null);

        Workout w = new Workout();
        w.setUserId(userId);
        w.setPlanId(resolvePlanId(userId, dto.getPlanId()));
        w.setTitle(dto.getTitle().trim());
        w.setStartedAt(now.minusSeconds(dto.getDurationSec() == null ? 0 : dto.getDurationSec()));
        w.setDurationSec(dto.getDurationSec() == null ? 0 : dto.getDurationSec());
        w.setTotalVolume(total);
        w.setNote(dto.getNote() == null ? "" : dto.getNote());
        workoutMapper.insert(w);

        List<WorkoutDetailVO.Block> blocks = writeBlocks(w.getId(), dto.getBlocks(), runningMax);
        return toDetail(w, now, blocks);
    }

    /**
     * 修改已有训练（详情页「编辑 → ✓」）：标题/备注覆盖，动作与组次**整体覆盖**（删旧写新），
     * 总容量重算；PR 按「排除本记录自身」的历史基线重判。来源计划、开始时间、时长不动。
     */
    @Transactional
    public WorkoutDetailVO update(Long userId, Long id, WorkoutUpdateDTO dto) {
        Workout w = requireOwn(userId, id);
        List<WorkoutCreateDTO.BlockDTO> blocks = dto.getBlocks();

        w.setTitle(dto.getTitle().trim());
        w.setNote(dto.getNote() == null ? "" : dto.getNote());
        w.setTotalVolume(sumVolume(blocks));
        w.setUpdatedAt(LocalDateTime.now());
        workoutMapper.updateById(w);

        // 动作与组次整体覆盖：先清旧（组次 → 动作条），再按新数据写回
        workoutMapper.deleteSetsByWorkoutId(id);
        blockMapper.delete(Wrappers.<WorkoutBlock>lambdaQuery().eq(WorkoutBlock::getWorkoutId, id));

        Map<Long, BigDecimal> runningMax = historyMax(userId, blocks, id);
        List<WorkoutDetailVO.Block> vbs = writeBlocks(id, blocks, runningMax);
        return toDetail(w, w.getCreatedAt(), vbs);
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
            vb.setExerciseId(b.getExerciseId());
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
        deleteOne(id);
    }

    /**
     * 批量删除（日志页长按多选删除）：先整体校验归属，再逐条删——任一不归属则整批回滚，
     * 不留「删了一半」的中间态。ids 去重后再删，避免重复 id 触发多余的删组次。
     */
    @Transactional
    public void batchDelete(Long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<Long> distinct = ids.stream().distinct().toList();
        for (Long id : distinct) {
            requireOwn(userId, id);
        }
        for (Long id : distinct) {
            deleteOne(id);
        }
    }

    private void deleteOne(Long id) {
        workoutMapper.deleteSetsByWorkoutId(id);
        blockMapper.delete(Wrappers.<WorkoutBlock>lambdaQuery().eq(WorkoutBlock::getWorkoutId, id));
        workoutMapper.deleteById(id);
    }

    /** 某动作的「上次数据」；没有记录时返回 null。 */
    public LastRecordVO lastRecord(Long userId, Long exerciseId) {
        return workoutMapper.selectLastRecord(userId, exerciseId);
    }

    /** 总容量＝Σ(重量×次数)，服务端自算、不信前端。 */
    private BigDecimal sumVolume(List<WorkoutCreateDTO.BlockDTO> blocks) {
        return blocks.stream()
                .flatMap(b -> b.getSets().stream())
                .map(s -> s.getWeight().multiply(BigDecimal.valueOf(s.getReps())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 这些动作的历史最大单组容量（weight×reps）——PR 判定的历史起点。
     * {@code excludeWorkoutId} 非空时排除该次训练（编辑场景：不拿自己当基线）。
     * 无挂库动作时不查库，直接空表。
     */
    private Map<Long, BigDecimal> historyMax(Long userId,
                                             List<WorkoutCreateDTO.BlockDTO> blocks,
                                             Long excludeWorkoutId) {
        Set<Long> exerciseIds = blocks.stream()
                .map(WorkoutCreateDTO.BlockDTO::getExerciseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (exerciseIds.isEmpty()) {
            return new HashMap<>();
        }
        List<ExerciseMaxVO> rows = excludeWorkoutId == null
                ? workoutMapper.selectMaxSetVolume(userId, exerciseIds)
                : workoutMapper.selectMaxSetVolumeExcluding(userId, exerciseIds, excludeWorkoutId);
        return new HashMap<>(rows.stream()
                .collect(Collectors.toMap(ExerciseMaxVO::getExerciseId, ExerciseMaxVO::getMaxVol)));
    }

    /** 写入动作条目与组次（序号从 1 起、跨动作重置），并顺带组装明细 VO。 */
    private List<WorkoutDetailVO.Block> writeBlocks(Long workoutId,
                                                    List<WorkoutCreateDTO.BlockDTO> blocks,
                                                    Map<Long, BigDecimal> runningMax) {
        List<WorkoutDetailVO.Block> out = new ArrayList<>();
        int order = 0;
        for (WorkoutCreateDTO.BlockDTO b : blocks) {
            WorkoutBlock block = new WorkoutBlock();
            block.setWorkoutId(workoutId);
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
            vb.setExerciseId(b.getExerciseId());
            vb.setName(block.getExerciseName());
            vb.setSets(sets);
            out.add(vb);
        }
        return out;
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
