package com.example.fitapp.module.plan.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.fitapp.common.BusinessException;
import com.example.fitapp.common.ResultCode;
import com.example.fitapp.module.exercise.entity.Exercise;
import com.example.fitapp.module.exercise.mapper.ExerciseMapper;
import com.example.fitapp.module.plan.dto.PlanSaveDTO;
import com.example.fitapp.module.plan.entity.Plan;
import com.example.fitapp.module.plan.entity.PlanExercise;
import com.example.fitapp.module.plan.entity.PlanExerciseSet;
import com.example.fitapp.module.plan.entity.WeekSchedule;
import com.example.fitapp.module.plan.mapper.PlanExerciseMapper;
import com.example.fitapp.module.plan.mapper.PlanExerciseSetMapper;
import com.example.fitapp.module.plan.mapper.PlanMapper;
import com.example.fitapp.module.plan.mapper.WeekScheduleMapper;
import com.example.fitapp.module.plan.vo.PlanDetailVO;
import com.example.fitapp.module.plan.vo.PlanListItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 训练计划：列表（带「上次」）、详情、新建、更新（整体替换编排）、删除。
 * 删除计划时同步移除周表中引用它的格子（回到「未安排」）。
 * 目标按组存：`plan_exercise_set` 一行一组，组数的唯一真相是行数（ADR 0027）。
 */
@Service
@RequiredArgsConstructor
public class PlanService {

    /** 列表卡动作名摘要最多显示几个（其余前端以「+N」呈现）。 */
    private static final int SUMMARY_SIZE = 3;

    /** 有氧记录口径（按层数录）——这类动作不参与循环组（ADR 0028）。 */
    private static final String METRIC_FLOORS = "floors";

    private final PlanMapper planMapper;
    private final PlanExerciseMapper planExerciseMapper;
    private final PlanExerciseSetMapper planExerciseSetMapper;
    private final WeekScheduleMapper weekScheduleMapper;
    private final ExerciseMapper exerciseMapper;
    private final PlanTrash planTrash;

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

        // 整体替换编排：先清子表再清编排，最后按提交顺序重插（数据量小，简单可靠优先）
        deleteSetsOfPlan(id);
        planExerciseMapper.delete(
                Wrappers.<PlanExercise>lambdaQuery().eq(PlanExercise::getPlanId, id));
        insertExercises(userId, id, dto.getExercises());
        return toDetail(p);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        // 快照要在删行之前存（ADR 0047），周安排占过哪几格也记在里面
        planTrash.put(userId, requireOwn(userId, id));
        deleteSetsOfPlan(id);
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
            if (item.getCircuitNo() != null && METRIC_FLOORS.equals(e.getMetric())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "有氧动作不参与循环");
            }

            List<PlanSaveDTO.SetDTO> sets = resolveSets(item);
            // 记录口径决定这一行存哪套目标值：floors（爬楼）只认层数，其余只认次数／重量。
            // 归一放在服务端：客户端多传的那几个字段一律丢弃，避免同一个数在两套语义里混用（ADR 0031）。
            boolean floorsMetric = METRIC_FLOORS.equals(e.getMetric());

            PlanExercise pe = new PlanExercise();
            pe.setPlanId(planId);
            pe.setExerciseId(e.getId());
            pe.setName(e.getName());
            pe.setMuscle(e.getMuscle());
            // 组数的唯一真相是 sets 行数；target_sets 只是同步过来的冗余列（ADR 0027）
            pe.setTargetSets(sets.isEmpty() ? null : sets.size());
            pe.setTargetReps(floorsMetric ? null : firstReps(sets));
            pe.setTargetWeight(floorsMetric ? null : firstWeight(sets));
            pe.setCircuitNo(item.getCircuitNo());
            pe.setSortOrder(order++);
            planExerciseMapper.insert(pe);

            int index = 1;
            for (PlanSaveDTO.SetDTO s : sets) {
                PlanExerciseSet row = new PlanExerciseSet();
                row.setPlanExerciseId(pe.getId());
                row.setSetIndex(index++);
                row.setReps(floorsMetric ? null : s.getReps());
                row.setWeight(floorsMetric ? null : s.getWeight());
                row.setFloors(floorsMetric ? s.getFloors() : null);
                planExerciseSetMapper.insert(row);
            }
        }
    }

    /**
     * 每组的来源：优先用 `sets`（长度即组数）；只传了 `targetSets` 的旧版请求按同值展开。
     * 两者都没有＝不设目标（0 行）。
     */
    private List<PlanSaveDTO.SetDTO> resolveSets(PlanSaveDTO.ItemDTO item) {
        if (item.getSets() != null && !item.getSets().isEmpty()) {
            return item.getSets();
        }
        if (item.getTargetSets() != null) {
            List<PlanSaveDTO.SetDTO> expanded = new ArrayList<>(item.getTargetSets());
            for (int i = 0; i < item.getTargetSets(); i++) {
                PlanSaveDTO.SetDTO s = new PlanSaveDTO.SetDTO();
                s.setReps(item.getTargetReps());
                s.setWeight(item.getTargetWeight());
                expanded.add(s);
            }
            return expanded;
        }
        return List.of();
    }

    private Integer firstReps(List<PlanSaveDTO.SetDTO> sets) {
        return sets.stream().map(PlanSaveDTO.SetDTO::getReps)
                .filter(Objects::nonNull).findFirst().orElse(null);
    }

    private BigDecimal firstWeight(List<PlanSaveDTO.SetDTO> sets) {
        return sets.stream().map(PlanSaveDTO.SetDTO::getWeight)
                .filter(Objects::nonNull).findFirst().orElse(null);
    }

    /** 子表没有外键级联，删编排前必须显式清掉它的组（否则留下孤儿行）。 */
    private void deleteSetsOfPlan(Long planId) {
        List<PlanExercise> old = planExerciseMapper.selectList(
                Wrappers.<PlanExercise>lambdaQuery().eq(PlanExercise::getPlanId, planId));
        if (old.isEmpty()) {
            return;
        }
        List<Long> ids = old.stream().map(PlanExercise::getId).toList();
        // 这里刻意用字符串列名而不是 lambda：纯单测里 MyBatis-Plus 的 lambda 缓存尚未建立，
        // lambda 写法会在单测中抛「can not find lambda cache」；字符串列名在单测与运行时完全等价。
        planExerciseSetMapper.delete(
                Wrappers.<PlanExerciseSet>query().in("plan_exercise_id", ids));
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

        Map<Long, List<PlanExerciseSet>> setsByPe = loadSets(rows);

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
            i.setCircuitNo(r.getCircuitNo());
            i.setSets(setsByPe.getOrDefault(r.getId(), List.of()).stream().map(s -> {
                PlanDetailVO.Set vo2 = new PlanDetailVO.Set();
                vo2.setSetIndex(s.getSetIndex());
                vo2.setReps(s.getReps());
                vo2.setWeight(s.getWeight());
                vo2.setFloors(s.getFloors());
                return vo2;
            }).toList());
            return i;
        }).toList());
        return vo;
    }

    /** 一次查完整个计划的组目标（避免每个动作一次查询）。 */
    private Map<Long, List<PlanExerciseSet>> loadSets(List<PlanExercise> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = rows.stream().map(PlanExercise::getId).toList();
        // 同 deleteSetsOfPlan：字符串列名，避免纯单测里的 lambda 缓存问题
        return planExerciseSetMapper.selectList(
                        Wrappers.<PlanExerciseSet>query()
                                .in("plan_exercise_id", ids)
                                .orderByAsc("set_index"))
                .stream()
                .collect(Collectors.groupingBy(PlanExerciseSet::getPlanExerciseId));
    }
}
