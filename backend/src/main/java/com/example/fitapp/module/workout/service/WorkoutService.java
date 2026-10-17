package com.example.fitapp.module.workout.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.fitapp.common.BusinessException;
import com.example.fitapp.common.ResultCode;
import com.example.fitapp.module.exercise.entity.Exercise;
import com.example.fitapp.module.exercise.mapper.ExerciseMapper;
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
import com.example.fitapp.module.workout.vo.CountVolumeVO;
import com.example.fitapp.module.workout.vo.ExerciseMaxVO;
import com.example.fitapp.module.workout.vo.LastRecordVO;
import com.example.fitapp.module.workout.vo.MonthSummaryVO;
import com.example.fitapp.module.workout.vo.WorkoutDetailVO;
import com.example.fitapp.module.workout.vo.WorkoutListItemVO;
import com.example.fitapp.module.workout.vo.WorkoutTotalsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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

    /** 有氧肌群码——有氧动作不参与总容量与 PR（ADR 0023／0024）。 */
    private static final String MUSCLE_CARDIO = "cardio";

    /** 记录口径：按层数录（爬楼）——这类组不用「重量×次数」（ADR 0025）。 */
    private static final String METRIC_FLOORS = "floors";

    private final WorkoutMapper workoutMapper;
    private final WorkoutBlockMapper blockMapper;
    private final WorkoutSetMapper setMapper;
    private final PlanMapper planMapper;
    private final ExerciseMapper exerciseMapper;

    @Transactional
    public WorkoutDetailVO create(Long userId, WorkoutCreateDTO dto) {
        LocalDateTime now = LocalDateTime.now();
        Map<Long, Exercise> exercises = exerciseByIds(blockExerciseIds(dto.getBlocks()));
        Set<Long> cardioIds = cardioIds(exercises);
        BigDecimal total = sumVolume(dto.getBlocks(), cardioIds);

        // PR 判定的起点＝这些动作的历史「最重一组」；本训练内逐组递进更新
        Map<Long, ExerciseMaxVO> runningMax = historyMax(userId, dto.getBlocks(), null);

        Workout w = new Workout();
        w.setUserId(userId);
        w.setPlanId(resolvePlanId(userId, dto.getPlanId()));
        w.setTitle(dto.getTitle().trim());
        w.setStartedAt(now.minusSeconds(dto.getDurationSec() == null ? 0 : dto.getDurationSec()));
        w.setDurationSec(dto.getDurationSec() == null ? 0 : dto.getDurationSec());
        w.setCalories(dto.getCalories());
        w.setTotalVolume(total);
        w.setNote(dto.getNote() == null ? "" : dto.getNote());
        workoutMapper.insert(w);

        List<WorkoutDetailVO.Block> blocks =
                writeBlocks(w.getId(), dto.getBlocks(), runningMax, cardioIds, exercises);
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
        Map<Long, Exercise> exercises = exerciseByIds(blockExerciseIds(blocks));
        Set<Long> cardioIds = cardioIds(exercises);

        w.setTitle(dto.getTitle().trim());
        w.setNote(dto.getNote() == null ? "" : dto.getNote());
        w.setTotalVolume(sumVolume(blocks, cardioIds));
        w.setCalories(dto.getCalories());
        w.setUpdatedAt(LocalDateTime.now());
        workoutMapper.updateById(w);
        // 消耗可清空：全局 update-strategy=not_null 会静默跳过 null，必须显式 set（同 week_schedule 的坑）
        if (dto.getCalories() == null) {
            workoutMapper.update(null, Wrappers.<Workout>update()
                    .eq("id", id)
                    .set("calories", null));
        }

        // 动作与组次整体覆盖：先清旧（组次 → 动作条），再按新数据写回
        workoutMapper.deleteSetsByWorkoutId(id);
        blockMapper.delete(Wrappers.<WorkoutBlock>lambdaQuery().eq(WorkoutBlock::getWorkoutId, id));

        Map<Long, ExerciseMaxVO> runningMax = historyMax(userId, blocks, id);
        List<WorkoutDetailVO.Block> vbs = writeBlocks(id, blocks, runningMax, cardioIds, exercises);
        return toDetail(w, w.getCreatedAt(), vbs);
    }

    /**
     * 日志列表：`month` 为空＝不过滤（首页「上次训练」拿它取最近一条）；否则按月过滤（左闭右开）。
     * `offset` 供触底分页——列表分页不影响概览数字（概览走 {@link #monthSummary}）。
     */
    public List<WorkoutListItemVO> list(Long userId, int limit, int offset, String month) {
        LocalDateTime start = null;
        LocalDateTime end = null;
        if (month != null && !month.isBlank()) {
            YearMonth m = parseMonth(month);
            start = m.atDay(1).atStartOfDay();
            end = m.plusMonths(1).atDay(1).atStartOfDay();
        }
        return workoutMapper.selectListItems(userId, start, end, limit, offset);
    }

    /**
     * 按月概览：当月次数与总容量、**上月同口径**（环比要有基准才算数，ADR 0023），
     * 外加**本周次数**（首页统计条也读这里，别再用封顶列表在前端过滤）。
     */
    public MonthSummaryVO monthSummary(Long userId, String month) {
        YearMonth m = parseMonth(month);
        CountVolumeVO cur = countVolume(userId, m);
        CountVolumeVO prev = countVolume(userId, m.minusMonths(1));
        LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        CountVolumeVO week = workoutMapper.selectCountVolume(userId,
                weekStart.atStartOfDay(), weekStart.plusWeeks(1).atStartOfDay());

        MonthSummaryVO vo = new MonthSummaryVO();
        vo.setMonth(m.toString());
        vo.setCount(cur.getCnt() == null ? 0 : cur.getCnt());
        vo.setTotalVolume(cur.getVol() == null ? BigDecimal.ZERO : cur.getVol());
        vo.setWeekCount(week.getCnt() == null ? 0 : week.getCnt());
        vo.setPrevCount(prev.getCnt() == null ? 0 : prev.getCnt());
        vo.setPrevTotalVolume(prev.getVol() == null ? BigDecimal.ZERO : prev.getVol());
        return vo;
    }

    private CountVolumeVO countVolume(Long userId, YearMonth month) {
        return workoutMapper.selectCountVolume(userId,
                month.atDay(1).atStartOfDay(), month.plusMonths(1).atDay(1).atStartOfDay());
    }

    /**
     * `YYYY-MM` → YearMonth；空＝当月。
     * 格式不对**直接 400**，不静默当成当月——否则页面会显示一个月的数据却挂着另一个月的标题。
     */
    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "月份参数应为 YYYY-MM");
        }
    }

    /** 「我的」页累计口径：全量次数与总容量（ADR 0023）。 */
    public WorkoutTotalsVO totals(Long userId) {
        return workoutMapper.selectTotals(userId);
    }

    public WorkoutDetailVO detail(Long userId, Long id) {
        Workout w = requireOwn(userId, id);
        List<WorkoutBlock> blocks = blockMapper.selectList(
                Wrappers.<WorkoutBlock>lambdaQuery()
                        .eq(WorkoutBlock::getWorkoutId, id)
                        .orderByAsc(WorkoutBlock::getSortOrder)
                        .orderByAsc(WorkoutBlock::getId));
        Map<Long, Exercise> exercises = exerciseByIds(blocks.stream()
                .map(WorkoutBlock::getExerciseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        Set<Long> cardioIds = cardioIds(exercises);
        List<WorkoutDetailVO.Block> vbs = new ArrayList<>();
        for (WorkoutBlock b : blocks) {
            List<WorkoutSet> sets = setMapper.selectList(
                    Wrappers.<WorkoutSet>lambdaQuery()
                            .eq(WorkoutSet::getBlockId, b.getId())
                            .orderByAsc(WorkoutSet::getSetIndex));
            WorkoutDetailVO.Block vb = new WorkoutDetailVO.Block();
            vb.setExerciseId(b.getExerciseId());
            vb.setName(b.getExerciseName());
            vb.setCardio(cardioIds.contains(b.getExerciseId()));
            vb.setMetric(metricOf(exercises, b.getExerciseId()));
            vb.setSets(sets.stream().map(s -> {
                WorkoutDetailVO.SetItem item = new WorkoutDetailVO.SetItem();
                item.setWeight(s.getWeight());
                item.setReps(s.getReps());
                item.setPr(Boolean.TRUE.equals(s.getPr()));
                item.setFloors(s.getFloors());
                item.setDurationSec(s.getDurationSec());
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

    /**
     * 总容量＝Σ(重量×次数)，**只累计力量动作**（有氧不计入，ADR 0023）；服务端自算、不信前端。
     * 未挂动作库的组（`exerciseId` 为 null）判不出肌群，按计入处理。
     */
    private BigDecimal sumVolume(List<WorkoutCreateDTO.BlockDTO> blocks, Set<Long> cardioIds) {
        return blocks.stream()
                .filter(b -> !cardioIds.contains(b.getExerciseId()))
                .flatMap(b -> b.getSets().stream())
                .map(s -> s.getWeight().multiply(BigDecimal.valueOf(s.getReps())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 组里所有挂了库的动作 id（未挂库的为 null，过滤掉）。 */
    private Set<Long> blockExerciseIds(List<WorkoutCreateDTO.BlockDTO> blocks) {
        return blocks.stream()
                .map(WorkoutCreateDTO.BlockDTO::getExerciseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * 批量取动作（肌群与记录口径都从这一份来）；下架（archived）的动作也查得到——
     * 历史记录仍要能判「有氧」与「按层数录」。
     */
    private Map<Long, Exercise> exerciseByIds(Collection<Long> exerciseIds) {
        if (exerciseIds.isEmpty()) {
            return new HashMap<>();
        }
        return exerciseMapper.selectBatchIds(exerciseIds).stream()
                .filter(e -> e.getId() != null)
                .collect(Collectors.toMap(Exercise::getId, e -> e, (a, b) -> a));
    }

    /**
     * 这些动作里哪些是有氧——容量与 PR 都不参与（ADR 0023／0024）。
     * ⚠️ 刻意用 `Collectors.toSet()`（HashSet）而非 `Set.of()`：后者对 `contains(null)` 抛 NPE，
     * 而未挂动作库的组正是 `exerciseId = null`。
     */
    private Set<Long> cardioIds(Map<Long, Exercise> exercises) {
        return exercises.values().stream()
                .filter(e -> MUSCLE_CARDIO.equals(e.getMuscle()))
                .map(Exercise::getId)
                .collect(Collectors.toSet());
    }

    /** 该动作的记录口径（`floors`＝按层数录）；未挂库、或库里没有这个动作时为 null。 */
    private String metricOf(Map<Long, Exercise> exercises, Long exerciseId) {
        Exercise e = exerciseId == null ? null : exercises.get(exerciseId);
        return e == null ? null : e.getMetric();
    }

    private boolean usesFloors(Map<Long, Exercise> exercises, Long exerciseId) {
        return METRIC_FLOORS.equals(metricOf(exercises, exerciseId));
    }

    /**
     * 这些动作的历史「最重一组」——PR 判定的历史起点。
     * {@code excludeWorkoutId} 非空时排除该次训练（编辑场景：不拿自己当基线）。
     * 无挂库动作时不查库，直接空表。
     */
    private Map<Long, ExerciseMaxVO> historyMax(Long userId,
                                                List<WorkoutCreateDTO.BlockDTO> blocks,
                                                Long excludeWorkoutId) {
        Set<Long> exerciseIds = blockExerciseIds(blocks);
        if (exerciseIds.isEmpty()) {
            return new HashMap<>();
        }
        List<ExerciseMaxVO> rows = excludeWorkoutId == null
                ? workoutMapper.selectMaxSetVolume(userId, exerciseIds)
                : workoutMapper.selectMaxSetVolumeExcluding(userId, exerciseIds, excludeWorkoutId);
        return new HashMap<>(rows.stream()
                .collect(Collectors.toMap(ExerciseMaxVO::getExerciseId, r -> r)));
    }

    /** 写入动作条目与组次（序号从 1 起、跨动作重置），并顺带组装明细 VO。 */
    private List<WorkoutDetailVO.Block> writeBlocks(Long workoutId,
                                                    List<WorkoutCreateDTO.BlockDTO> blocks,
                                                    Map<Long, ExerciseMaxVO> runningMax,
                                                    Set<Long> cardioIds,
                                                    Map<Long, Exercise> exercises) {
        List<WorkoutDetailVO.Block> out = new ArrayList<>();
        int order = 0;
        for (WorkoutCreateDTO.BlockDTO b : blocks) {
            WorkoutBlock block = new WorkoutBlock();
            block.setWorkoutId(workoutId);
            block.setExerciseId(b.getExerciseId());
            block.setExerciseName(b.getName().trim());
            block.setSortOrder(order++);
            blockMapper.insert(block);

            // floors 口径（爬楼）：只信层数，重量／次数统一存占位值 0／1——
            // 避免「同一个数在两套语义里混用」（被否的复用次数栏方案，ADR 0025）
            boolean floors = usesFloors(exercises, b.getExerciseId());

            List<WorkoutDetailVO.SetItem> sets = new ArrayList<>();
            int idx = 1;
            for (WorkoutCreateDTO.SetDTO s : b.getSets()) {
                WorkoutSet ws = new WorkoutSet();
                ws.setBlockId(block.getId());
                ws.setSetIndex(idx++);
                ws.setWeight(floors ? BigDecimal.ZERO : s.getWeight());
                ws.setReps(floors ? 1 : s.getReps());
                ws.setFloors(floors ? s.getFloors() : null);
                ws.setPr(isPr(b.getExerciseId(), s, runningMax, cardioIds));
                ws.setDurationSec(s.getDurationSec());
                setMapper.insert(ws);

                WorkoutDetailVO.SetItem item = new WorkoutDetailVO.SetItem();
                item.setWeight(ws.getWeight());
                item.setReps(ws.getReps());
                item.setFloors(ws.getFloors());
                item.setPr(ws.getPr());
                item.setDurationSec(s.getDurationSec());
                sets.add(item);
            }

            WorkoutDetailVO.Block vb = new WorkoutDetailVO.Block();
            vb.setExerciseId(b.getExerciseId());
            vb.setName(block.getExerciseName());
            vb.setCardio(cardioIds.contains(b.getExerciseId()));
            vb.setMetric(metricOf(exercises, b.getExerciseId()));
            vb.setSets(sets);
            out.add(vb);
        }
        return out;
    }

    /**
     * PR 口径（scope #7 ＋ ADR 0024）：该动作历史「最重一组」——**先比重量、同重量比次数**。
     * 严格大于才刷新（并列不算新 PR）；无历史即当前最佳＝PR；
     * 未挂动作库的组不判定；**有氧动作不参与**（其「重量」没有可比性）。
     */
    private boolean isPr(Long exerciseId, WorkoutCreateDTO.SetDTO s,
                         Map<Long, ExerciseMaxVO> runningMax, Set<Long> cardioIds) {
        if (exerciseId == null || cardioIds.contains(exerciseId)) {
            return false;
        }
        ExerciseMaxVO best = runningMax.get(exerciseId);
        if (best != null && !beats(s, best)) {
            return false;
        }
        ExerciseMaxVO cur = new ExerciseMaxVO();
        cur.setExerciseId(exerciseId);
        cur.setWeight(s.getWeight());
        cur.setReps(s.getReps());
        runningMax.put(exerciseId, cur);
        return true;
    }

    /** 本组是否比历史最佳更重（同重量再比次数）——**严格大于**才算法。 */
    private boolean beats(WorkoutCreateDTO.SetDTO s, ExerciseMaxVO best) {
        int byWeight = s.getWeight().compareTo(best.getWeight());
        return byWeight > 0 || (byWeight == 0 && s.getReps() > best.getReps());
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
        vo.setCalories(w.getCalories());
        vo.setTotalVolume(w.getTotalVolume());
        vo.setNote(w.getNote());
        vo.setBlocks(blocks);
        return vo;
    }
}
