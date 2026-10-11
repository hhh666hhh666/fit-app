package com.example.fitapp.module.workout.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.fitapp.module.workout.entity.Workout;
import com.example.fitapp.module.workout.vo.LastRecordVO;
import com.example.fitapp.module.workout.vo.WorkoutListItemVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface WorkoutMapper extends BaseMapper<Workout> {

    /** 日志列表：每条带「第一个动作名」概要，按时间倒序。 */
    @Select("""
            SELECT w.id, w.title, w.duration_sec AS durationSec, w.total_volume AS totalVolume,
                   w.created_at AS createdAt,
                   (SELECT wb.exercise_name FROM workout_block wb
                    WHERE wb.workout_id = w.id ORDER BY wb.sort_order LIMIT 1) AS firstExercise
            FROM workout w
            WHERE w.user_id = #{userId}
            ORDER BY w.created_at DESC
            LIMIT #{limit}
            """)
    List<WorkoutListItemVO> selectListItems(@Param("userId") Long userId, @Param("limit") int limit);

    /** 某动作的「上次数据」：最近一次训练里该动作的最后一组。 */
    @Select("""
            SELECT ws.weight, ws.reps, w.created_at AS performedAt
            FROM workout_set ws
            JOIN workout_block wb ON wb.id = ws.block_id
            JOIN workout w ON w.id = wb.workout_id
            WHERE w.user_id = #{userId} AND wb.exercise_id = #{exerciseId}
            ORDER BY w.created_at DESC, ws.set_index DESC
            LIMIT 1
            """)
    LastRecordVO selectLastRecord(@Param("userId") Long userId, @Param("exerciseId") Long exerciseId);

    /** 删除一次训练的全部组次（按 workout 维度，join 到 block）。 */
    @Delete("""
            DELETE ws FROM workout_set ws
            JOIN workout_block wb ON wb.id = ws.block_id
            WHERE wb.workout_id = #{workoutId}
            """)
    int deleteSetsByWorkoutId(@Param("workoutId") Long workoutId);
}
