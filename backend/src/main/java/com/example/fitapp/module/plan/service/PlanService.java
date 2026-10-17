package com.example.fitapp.module.plan.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.fitapp.common.BusinessException;
import com.example.fitapp.common.ResultCode;
import com.example.fitapp.module.exercise.entity.Exercise;
import com.example.fitapp.module.exercise.mapper.ExerciseMapper;
import com.example.fitapp.module.plan.dto.PlanSaveDTO;
import com.example.fitapp.module.plan.entity.Plan;
import com.example.fitapp.module.plan.entity.PlanExercise;
import com.example.fitapp.module.plan.entity.WeekSchedule;
import com.example.fitapp.module.plan.mapper.PlanExerciseMapper;
import com.example.fitapp.module.plan.mapper.PlanMapper;
import com.example.fitapp.module.plan.mapper.WeekScheduleMapper;
import com.example.fitapp.module.plan.vo.PlanDetailVO;
import com.example.fitapp.module.plan.vo.PlanListItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 训练计划：列表（带「上次」）、详情、新建、更新（整体替换编排）、删除。
 * 删除计划时同步移除周表中引用它的格子（回到「未安排」）。
 */
@Service
@RequiredArgsConstructor
public class PlanService {

    /** 列表卡动作名摘要最多显示几个（其余前端以「+N」呈现）。 */
    private static final int SUMMARY_SIZE = 3;

    private final PlanMapper planMapper;
    private final PlanExerciseMapper planExerciseMapper;
    private final WeekScheduleMapper weekScheduleMapper;
    private final ExerciseMapper exerciseMapper;

    public List<PlanListItemVO> list(Long userId) {
        List<PlanListItemVO> rows = planMapper.selectListItems(userId);
        if (rows.isEmpty()) {
            return rows;
        }
        List<Long> ids = rows.stream().map(PlanListItemVO::getId).toList();
        List<PlanExercise> all = planExerciseMapper.selectList(
                Wrappers.<PlanExercise>lambdaQuery()
                        .in(PlanExercise::getPlanId, ids)
                        .orderByAsc(PlanExercise::getPlanId)
                        .orderByAsc(PlanExercise::getSortOrder)
                        .orderByAsc(PlanExercise::getId));
        Map<Long, List<String>> namesByPlan = all.stream().collect(Collectors.groupingBy(
                PlanExercise::getPlanId,
                Collectors.mapping(PlanExercise::getName, Collectors.toList())));
        for (PlanListItemVO vo : rows) {
            List<String> names = namesByPlan.getOrDefault(vo.getId(), List.of());
            vo.setExerciseNames(names.size() <= SUMMARY_SIZE ? names : names.subList(0, SUMMARY_SIZE));
        }
        return rows;
    }

    public PlanDetailVO detail(Long userId, Long id) {
        return toDetail(requireOwn(userId, id));
    }

    @Transactional
    public PlanDetailVO create(Long userId, PlanSaveDTO dto) {
        String name = dto.getName().trim();
        checkNameFree(userId, name, null);

        Plan p = new Plan();
        p.setUserId(userId);
        p.setName(name);
        planMapper.insert(p);

        insertExercises(userId, p.getId(), dto.getExercises());
        return toDetail(p);
    }

    @Transactional
    public PlanDetailVO update(Long userId, Long id, PlanSaveDTO dto) {
        Plan p = requireOwn(userId, id);
        String name = dto.getName().trim();
        checkNameFree(userId, name, id);

        p.setName(name);
        planMapper.updateById(p);

        // 整体替换编排：清空后按提交顺序重插（数据量小，简单可靠优先）
        planExerciseMapper.delete(
                Wrappers.<PlanExercise>lambdaQuery().eq(PlanExercise::getPlanId, id));
        insertExercises(userId, id, dto.getExercises());
        return toDetail(p);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        requireOwn(userId, id);
        planExerciseMapper.delete(
                Wrappers.<PlanExercise>lambdaQuery().eq(PlanExercise::getPlanId, id));
        // 周表引用一并移除（这些格子回到「未安排」；历史训练靠 workout.plan_id 弱引用与 title 快照，不受影响）
        weekScheduleMapper.delete(
                Wrappers.<WeekSchedule>lambdaQuery()
                        .eq(WeekSchedule::getUserId, userId)
                        .eq(WeekSchedule::getPlanId, id));
        planMapper.deleteById(id);
    }

    private void insertExercises(Long userId, Long planId, List<PlanSaveDTO.ItemDTO> items) {
        Set<Long> seen = new HashSet<>();
        int order = 0;
        for (PlanSaveDTO.ItemDTO item : items) {
            if (!seen.add(item.getExerciseId())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "同一动作不能重复添加");
            }
            Exercise e = exerciseMapper.selectById(item.getExerciseId());
            boolean usable = e != null
                    && !Boolean.TRUE.equals(e.getArchived())
                    && (e.getUserId() == null || e.getUserId().equals(userId));
            if (!usable) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "动作不存在或已下架");
            }
            PlanExercise pe = new PlanExercise();
            pe.setPlanId(planId);
            pe.setExerciseId(e.getId());
            pe.setName(e.getName());
            pe.setMuscle(e.getMuscle());
            pe.setTargetSets(item.getTargetSets());
            pe.setTargetReps(item.getTargetReps());
            pe.setTargetWeight(item.getTargetWeight());
            pe.setSortOrder(order++);
            planExerciseMapper.insert(pe);
        }
    }

    /** 同用户内计划名不重名；更新时排除自己。 */
    private void checkNameFree(Long userId, String name, Long excludeId) {
        Long count = planMapper.selectCount(
                Wrappers.<Plan>lambdaQuery()
                        .eq(Plan::getUserId, userId)
                        .eq(Plan::getName, name)
                        .ne(excludeId != null, Plan::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "已有同名计划");
        }
    }

    private Plan requireOwn(Long userId, Long id) {
        Plan p = planMapper.selectById(id);
        if (p == null || !userId.equals(p.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "计划不存在");
        }
        return p;
    }

    private PlanDetailVO toDetail(Plan p) {
        List<PlanExercise> rows = planExerciseMapper.selectList(
                Wrappers.<PlanExercise>lambdaQuery()
                        .eq(PlanExercise::getPlanId, p.getId())
                        .orderByAsc(PlanExercise::getSortOrder)
                        .orderByAsc(PlanExercise::getId));
        // 记录口径取动作库实时值：爬楼要按层数录（ADR 0025）；动作未挂库时为 null
        Set<Long> exerciseIds = rows.stream()
                .map(PlanExercise::getExerciseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> metrics = exerciseIds.isEmpty() ? Map.of()
                : exerciseMapper.selectBatchIds(exerciseIds).stream()
                        .filter(e -> e.getId() != null && e.getMetric() != null)
                        .collect(Collectors.toMap(Exercise::getId, Exercise::getMetric, (a, b) -> a));

        PlanDetailVO vo = new PlanDetailVO();
        vo.setId(p.getId());
        vo.setName(p.getName());
        vo.setExercises(rows.stream().map(r -> {
            PlanDetailVO.Item i = new PlanDetailVO.Item();
            i.setExerciseId(r.getExerciseId());
            i.setName(r.getName());
            i.setMuscle(r.getMuscle());
            i.setMetric(metrics.get(r.getExerciseId()));
            i.setTargetSets(r.getTargetSets());
            i.setTargetReps(r.getTargetReps());
            i.setTargetWeight(r.getTargetWeight());
            return i;
        }).toList());
        return vo;
    }
}
