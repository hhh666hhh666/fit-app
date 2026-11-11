package com.example.fitapp.module.exercise.vo;

import java.util.List;

/**
 * 动作详情页（ADR 0055）：列表里那几项，加上动作资料。资料只有导入过数据集的预置才有，
 * 自定义动作全为空；media=true 表示有动图可取（{@code GET /api/exercises/{id}/media}）。
 */
public record ExerciseDetailVO(Long id, String name, String muscle, String metric, boolean preset, boolean archived,
                               String nameEn, String equipment, String target, List<String> secondary,
                               List<String> steps, boolean media) {
}
