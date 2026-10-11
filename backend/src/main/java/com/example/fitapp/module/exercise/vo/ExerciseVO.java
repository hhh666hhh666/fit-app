package com.example.fitapp.module.exercise.vo;

/**
 * 动作库条目。preset=true 为预设动作（user_id 为空）。
 */
public record ExerciseVO(Long id, String name, String muscle, boolean preset) {
}
