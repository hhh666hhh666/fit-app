package com.example.fitapp.module.exercise.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.fitapp.common.BusinessException;
import com.example.fitapp.common.ResultCode;
import com.example.fitapp.module.exercise.dto.ExerciseUpsertDTO;
import com.example.fitapp.module.exercise.entity.Exercise;
import com.example.fitapp.module.exercise.mapper.ExerciseMapper;
import com.example.fitapp.module.exercise.vo.ExerciseVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

    public List<ExerciseVO> list(Long userId, String muscle, String keyword) {
        List<Exercise> rows = exerciseMapper.selectList(
                Wrappers.<Exercise>lambdaQuery()
                        .eq(Exercise::getArchived, false)
                        .and(w -> w.eq(Exercise::getUserId, userId).or().isNull(Exercise::getUserId))
                        .eq(StringUtils.hasText(muscle), Exercise::getMuscle, muscle)
                        .like(StringUtils.hasText(keyword), Exercise::getName, keyword)
                        .orderByAsc(Exercise::getSortOrder)
                        .orderByAsc(Exercise::getId));
        return rows.stream().map(this::toVO).toList();
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

    /** 删除 = 下架（archived=1），历史记录照常显示。 */
    public void archive(Long userId, Long id) {
        Exercise e = requireOwn(userId, id);
        e.setArchived(true);
        exerciseMapper.updateById(e);
    }

    /** 批量下架：先校验全部归属，再统一更新，避免部分动作被误下架。 */
    @org.springframework.transaction.annotation.Transactional
    public void batchArchive(Long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        List<Long> distinct = ids.stream().distinct().toList();
        for (Long id : distinct) requireOwn(userId, id);
        exerciseMapper.update(null, Wrappers.<Exercise>update()
                .in("id", distinct)
                .eq("user_id", userId)
                .set("archived", true));
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
        return new ExerciseVO(e.getId(), e.getName(), e.getMuscle(), e.getMetric(), e.getUserId() == null);
    }
}
