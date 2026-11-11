package com.example.fitapp.module.exercise.vo;

/**
 * 动作库条目。preset=true 为预设动作（user_id 为空）；archived=true 只会出现在预置上：
 * 已下架、但这个用户用过（ADR 0048），前端带「已下架」标签。
 * nameEn 只有导入过数据集的预置才有（ADR 0055），动作库搜索也匹配它。
 */
public record ExerciseVO(Long id, String name, String muscle, String metric, boolean preset, boolean archived,
                         String nameEn) {
}
