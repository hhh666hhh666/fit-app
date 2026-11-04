package com.example.fitapp.module.exercise.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.fitapp.module.exercise.entity.Exercise;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ExerciseMapper extends BaseMapper<Exercise> {

    /** 这个用户用过这个动作：训练或计划引用过（ADR 0048 的「用过」）。 */
    @Select("""
            SELECT (EXISTS (SELECT 1 FROM workout_block wb JOIN workout w ON w.id = wb.workout_id
                            WHERE w.user_id = #{userId} AND wb.exercise_id = #{exerciseId})
                 OR EXISTS (SELECT 1 FROM plan_exercise pe JOIN plan p ON p.id = pe.plan_id
                            WHERE p.user_id = #{userId} AND pe.exercise_id = #{exerciseId}))
            """)
    boolean usedBy(@Param("userId") Long userId, @Param("exerciseId") Long exerciseId);

    /** 任何人的训练或计划引用过这个动作：引用过就锁定记录口径（ADR 0048）。 */
    @Select("""
            SELECT (EXISTS (SELECT 1 FROM workout_block WHERE exercise_id = #{exerciseId})
                 OR EXISTS (SELECT 1 FROM plan_exercise WHERE exercise_id = #{exerciseId}))
            """)
    boolean usedByAnyone(@Param("exerciseId") Long exerciseId);
}
