package com.example.fitapp.module.admin.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.fitapp.common.BusinessException;
import com.example.fitapp.common.ResultCode;
import com.example.fitapp.module.admin.dto.PresetExerciseSaveDTO;
import com.example.fitapp.module.admin.vo.AdminExerciseVO;
import com.example.fitapp.module.exercise.entity.Exercise;
import com.example.fitapp.module.exercise.mapper.ExerciseMapper;
import com.example.fitapp.module.exercise.service.ExerciseService;
import com.example.fitapp.module.preset.entity.PresetHidden;
import com.example.fitapp.module.preset.service.PresetHiddenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * 管理端的预置动作（ADR 0048）：列表、增改、下架与上架。不做导入导出。
 * 被任何训练或计划引用过的动作锁定记录口径——改了会让历史组次的含义变掉（ADR 0025）。
 */
@Service
@RequiredArgsConstructor
public class AdminExerciseService {

    static final String METRIC_DEFAULT = "weight_reps";

    private final ExerciseMapper exerciseMapper;
    private final PresetHiddenService presetHiddenService;

    /** 全部预置（几十条，不分页）：按排序、id。 */
    public List<AdminExerciseVO> list(String q, String muscle, String status) {
        String kw = q == null ? null : q.trim();
        return exerciseMapper.selectList(Wrappers.<Exercise>lambdaQuery()
                        .isNull(Exercise::getUserId)
                        .like(StringUtils.hasText(kw), Exercise::getName, kw)
                        .eq(StringUtils.hasText(muscle), Exercise::getMuscle, muscle)
                        .eq(AdminFoodService.STATUS_LISTED.equals(status), Exercise::getArchived, false)
                        .eq(AdminFoodService.STATUS_ARCHIVED.equals(status), Exercise::getArchived, true)
                        .orderByAsc(Exercise::getSortOrder)
                        .orderByAsc(Exercise::getId))
                .stream()
                .map(e -> toVO(e, exerciseMapper.usedByAnyone(e.getId())))
                .toList();
    }

    public AdminExerciseVO create(PresetExerciseSaveDTO dto) {
        checkMuscle(dto.getMuscle());
        Exercise e = new Exercise();
        e.setUserId(null);
        e.setName(dto.getName().trim());
        e.setMuscle(dto.getMuscle());
        e.setMetric(dto.getMetric() == null ? METRIC_DEFAULT : dto.getMetric());
        e.setArchived(false);
        e.setSortOrder(dto.getSortOrder() == null ? nextSortOrder() : dto.getSortOrder());
        exerciseMapper.insert(e);
        return toVO(exerciseMapper.selectById(e.getId()), false);
    }

    public AdminExerciseVO update(Long id, PresetExerciseSaveDTO dto) {
        checkMuscle(dto.getMuscle());
        Exercise e = requirePreset(id);
        boolean used = exerciseMapper.usedByAnyone(id);
        String metric = dto.getMetric() == null ? e.getMetric() : dto.getMetric();
        if (used && !Objects.equals(metric, e.getMetric())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "这个动作已经有人练过或排进计划，记录口径不能再改");
        }
        exerciseMapper.update(null, Wrappers.<Exercise>lambdaUpdate()
                .eq(Exercise::getId, id)
                .isNull(Exercise::getUserId)
                .set(Exercise::getName, dto.getName().trim())
                .set(Exercise::getMuscle, dto.getMuscle())
                .set(Exercise::getMetric, metric)
                .set(Exercise::getSortOrder, dto.getSortOrder() == null ? e.getSortOrder() : dto.getSortOrder()));
        return toVO(exerciseMapper.selectById(id), used);
    }

    public void archive(List<Long> ids) {
        setArchived(requirePresets(ids), true);
    }

    /** 重新上架：所有人恢复可见，用户之前的隐藏连同回收站里的条目一并作废。 */
    @Transactional
    public void unarchive(List<Long> ids) {
        List<Long> presets = requirePresets(ids);
        setArchived(presets, false);
        presetHiddenService.clearAll(PresetHidden.EXERCISE, presets);
    }

    private void setArchived(List<Long> ids, boolean archived) {
        exerciseMapper.update(null, Wrappers.<Exercise>lambdaUpdate()
                .in(Exercise::getId, ids)
                .isNull(Exercise::getUserId)
                .set(Exercise::getArchived, archived));
    }

    private int nextSortOrder() {
        Exercise last = exerciseMapper.selectOne(Wrappers.<Exercise>lambdaQuery()
                .isNull(Exercise::getUserId)
                .orderByDesc(Exercise::getSortOrder)
                .last("LIMIT 1"));
        return last == null || last.getSortOrder() == null ? 1 : last.getSortOrder() + 1;
    }

    private Exercise requirePreset(Long id) {
        Exercise e = id == null ? null : exerciseMapper.selectById(id);
        if (e == null || e.getUserId() != null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "预置动作不存在");
        }
        return e;
    }

    private List<Long> requirePresets(List<Long> ids) {
        List<Long> distinct = ids.stream().distinct().toList();
        Long count = exerciseMapper.selectCount(Wrappers.<Exercise>lambdaQuery()
                .in(Exercise::getId, distinct)
                .isNull(Exercise::getUserId));
        if (count == null || count != distinct.size()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "选中的条目里有不是预置动作的");
        }
        return distinct;
    }

    private static void checkMuscle(String muscle) {
        if (!ExerciseService.MUSCLES.contains(muscle)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "肌群分类不正确");
        }
    }

    private static AdminExerciseVO toVO(Exercise e, boolean used) {
        return new AdminExerciseVO(e.getId(), e.getName(), e.getMuscle(), e.getMetric(), e.getSortOrder(),
                Boolean.TRUE.equals(e.getArchived()), used, e.getUpdatedAt());
    }
}
