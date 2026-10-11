package com.example.fitapp.module.workout.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 训练中的动作条目。exercise_id 为弱引用（动作不物删）；exercise_name 为名称快照兜底。
 */
@Data
@TableName("workout_block")
public class WorkoutBlock {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workoutId;

    private Long exerciseId;

    private String exerciseName;

    private Integer sortOrder;

    private LocalDateTime createdAt;
}
