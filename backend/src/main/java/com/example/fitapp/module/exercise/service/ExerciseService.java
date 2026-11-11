package com.example.fitapp.module.exercise.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.fitapp.common.BusinessException;
import com.example.fitapp.common.ResultCode;
import com.example.fitapp.module.exercise.dto.ExerciseUpsertDTO;
import com.example.fitapp.module.exercise.entity.Exercise;
import com.example.fitapp.module.exercise.mapper.ExerciseMapper;
import com.example.fitapp.module.exercise.storage.ExerciseMediaStorage;
import com.example.fitapp.module.exercise.vo.ExerciseDetailVO;
import com.example.fitapp.module.exercise.vo.ExerciseVO;
import com.example.fitapp.module.preset.PresetSql;
import com.example.fitapp.module.preset.entity.PresetHidden;
import com.example.fitapp.module.preset.service.PresetHiddenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 动作库：预设（user_id 为 NULL）＋ 当前用户的自定义动作。
 * 被历史引用的动作不物理删除——「删除」= archived 置 1（从可选列表下架）。
 */
@Service
@RequiredArgsConstructor
public class ExerciseService {

    /** 合法肌群码（与前端显示一一对应，真相源见 CONTEXT.md 的「肌群」词条）。 */
    public static final Set<String> MUSCLES =
            Set.of("chest", "back", "leg", "shoulder", "arm", "core", "cardio");

    private final ExerciseMapper exerciseMapper;
    private final ExerciseTrash exerciseTrash;
    private final PresetHiddenService presetHiddenService;
    private final ExerciseMediaStorage mediaStorage;

    public List<ExerciseVO> list(Long userId, String muscle, String keyword) {
        List<Exercise> rows = exerciseMapper.selectList(
                Wrappers.<Exercise>lambdaQuery()
                        // 列表用不着要领，别把上千段长文本读出来（ADR 0055）
                        .select(Exercise.class, f -> !"steps".equals(f.getColumn()))
                        // 没删的自定义动作 ＋ 对这个用户可见的预置（已下架的只给用过、没隐藏的人看，ADR 0048）
                        .apply(PresetSql.EXERCISE_VISIBLE, userId)
                        .eq(StringUtils.hasText(muscle), Exercise::getMuscle, muscle)
                        .and(StringUtils.hasText(keyword), w -> w
                                .like(Exercise::getName, keyword)
                                .or()
                                .like(Exercise::getNameEn, keyword))
                        .orderByAsc(Exercise::getSortOrder)
                        .orderByAsc(Exercise::getId));
        return rows.stream().map(this::toVO).toList();
    }

    /** 动作库里看得到的动作数（「我的」页用）：导入数据集后一千多条，别为一个数字拉全量列表。 */
    public long count(Long userId) {
        Long n = exerciseMapper.selectCount(Wrappers.<Exercise>lambdaQuery()
                .apply(PresetSql.EXERCISE_VISIBLE, userId));
        return n == null ? 0 : n;
    }

    /** 动作详情（ADR 0055）：只给这个用户在动作库里看得到的动作。 */
    public ExerciseDetailVO detail(Long userId, Long id) {
        Exercise e = requireVisible(userId, id);
        boolean preset = e.getUserId() == null;
        return new ExerciseDetailVO(e.getId(), e.getName(), e.getMuscle(), e.getMetric(), preset,
                preset && Boolean.TRUE.equals(e.getArchived()),
                e.getNameEn(), e.getEquipment(), e.getTarget(), split(e.getSecondary(), "、"),
                split(e.getSteps(), "\n"), mediaStorage.find(e.getMedia(), ExerciseMediaStorage.Kind.GIF) != null);
    }

    /** 动图或缩略图文件；动作看不到、没有资料或文件不在都按 404。 */
    public java.nio.file.Path media(Long userId, Long id, ExerciseMediaStorage.Kind kind) {
        java.nio.file.Path p = mediaStorage.find(requireVisible(userId, id).getMedia(), kind);
        if (p == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "这个动作没有动图");
        }
        return p;
    }

    private Exercise requireVisible(Long userId, Long id) {
        Exercise e = id == null ? null : exerciseMapper.selectOne(Wrappers.<Exercise>lambdaQuery()
                .eq(Exercise::getId, id)
                .apply(PresetSql.EXERCISE_VISIBLE, userId));
        if (e == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "动作不存在");
        }
        return e;
    }

    private static List<String> split(String text, String sep) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        return Arrays.stream(text.split(sep)).map(String::trim).filter(StringUtils::hasText).toList();
    }

    public ExerciseVO create(Long userId, ExerciseUpsertDTO dto) {
        checkMuscle(dto.getMuscle());
        Exercise e = new Exercise();
        e.setUserId(userId);
        e.setName(dto.getName().trim());
        e.setMuscle(dto.getMuscle());
        e.setArchived(false);
        e.setSortOrder(0);
        exerciseMapper.insert(e);
        return toVO(e);
    }

    public ExerciseVO update(Long userId, Long id, ExerciseUpsertDTO dto) {
        checkMuscle(dto.getMuscle());
        Exercise e = requireOwn(userId, id);
        e.setName(dto.getName().trim());
        e.setMuscle(dto.getMuscle());
        exerciseMapper.updateById(e);
        return toVO(e);
    }

    /**
     * 删除 = 下架（archived=1），历史记录照常显示；回收站里留一条恢复入口（ADR 0047）。
     * 已下架、这个用户还看得到的预置，删除只是对这个用户隐藏（ADR 0048）。
     */
    @org.springframework.transaction.annotation.Transactional
    public void archive(Long userId, Long id) {
        Exercise preset = archivedPresetVisibleTo(userId, id);
        if (preset != null) {
            hide(userId, preset);
            return;
        }
        Exercise e = requireOwn(userId, id);
        e.setArchived(true);
        exerciseMapper.updateById(e);
        exerciseTrash.put(userId, e);
    }

    /** 批量下架：先校验全部归属，再统一更新，避免部分动作被误下架。 */
    @org.springframework.transaction.annotation.Transactional
    public void batchArchive(Long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        List<Long> distinct = ids.stream().distinct().toList();
        List<Exercise> presets = new ArrayList<>();
        List<Exercise> own = new ArrayList<>();
        for (Long id : distinct) {
            Exercise preset = archivedPresetVisibleTo(userId, id);
            if (preset != null) {
                presets.add(preset);
            } else {
                own.add(requireOwn(userId, id));
            }
        }
        if (!own.isEmpty()) {
            exerciseMapper.update(null, Wrappers.<Exercise>update()
                    .in("id", own.stream().map(Exercise::getId).toList())
                    .eq("user_id", userId)
                    .set("archived", true));
            own.forEach(e -> exerciseTrash.put(userId, e));
        }
        presets.forEach(p -> hide(userId, p));
    }

    /** 已下架、而且这个用户用过的预置；不是这种情况返回 null。 */
    private Exercise archivedPresetVisibleTo(Long userId, Long id) {
        Exercise e = exerciseMapper.selectById(id);
        if (e == null || e.getUserId() != null || !Boolean.TRUE.equals(e.getArchived())) {
            return null;
        }
        return exerciseMapper.usedBy(userId, id) ? e : null;
    }

    private void hide(Long userId, Exercise e) {
        presetHiddenService.hide(userId, PresetHidden.EXERCISE, e.getId(), e.getName(), "已下架");
    }

    private Exercise requireOwn(Long userId, Long id) {
        Exercise e = exerciseMapper.selectById(id);
        if (e == null || Boolean.TRUE.equals(e.getArchived())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "动作不存在");
        }
        if (!userId.equals(e.getUserId())) {
            // 预设动作或他人动作：不可改删
            throw new BusinessException(ResultCode.FORBIDDEN, "只能修改自己创建的动作");
        }
        return e;
    }

    private void checkMuscle(String muscle) {
        if (!MUSCLES.contains(muscle)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "肌群分类不正确");
        }
    }

    private ExerciseVO toVO(Exercise e) {
        boolean preset = e.getUserId() == null;
        return new ExerciseVO(e.getId(), e.getName(), e.getMuscle(), e.getMetric(), preset,
                preset && Boolean.TRUE.equals(e.getArchived()), e.getNameEn());
    }
}
