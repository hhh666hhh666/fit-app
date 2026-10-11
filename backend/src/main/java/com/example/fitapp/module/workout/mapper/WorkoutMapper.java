package com.example.fitapp.module.workout.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.fitapp.module.workout.entity.Workout;
import com.example.fitapp.module.workout.vo.CountDurationVO;
import com.example.fitapp.module.workout.vo.ExerciseMaxVO;
import com.example.fitapp.module.workout.vo.LastRecordVO;
import com.example.fitapp.module.workout.vo.WorkoutListItemVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Collection;
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

    /** 这些动作的历史最大单组容量（weight×reps）——PR 判定的历史起点。 */
    @Select("""
            <script>
            SELECT wb.exercise_id AS exerciseId, MAX(ws.weight * ws.reps) AS maxVol
            FROM workout_set ws
            JOIN workout_block wb ON wb.id = ws.block_id
            JOIN workout w ON w.id = wb.workout_id
            WHERE w.user_id = #{userId} AND wb.exercise_id IN
            <foreach collection="exerciseIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY wb.exercise_id
            </script>
            """)
    List<ExerciseMaxVO> selectMaxSetVolume(@Param("userId") Long userId,
                                           @Param("exerciseIds") Collection<Long> exerciseIds);

    /** 某起点以来的训练时间列表（统计页按周分桶用，自用数据量小）。 */
    @Select("""
            SELECT w.created_at FROM workout w
            WHERE w.user_id = #{userId} AND w.created_at >= #{start}
            """)
    List<LocalDateTime> selectCreatedAtSince(@Param("userId") Long userId, @Param("start") LocalDateTime start);

    /** 周期内训练次数与总时长（秒）。 */
    @Select("""
            SELECT COUNT(*) AS cnt, COALESCE(SUM(w.duration_sec), 0) AS dur
            FROM workout w
            WHERE w.user_id = #{userId} AND w.created_at >= #{start}
            """)
    CountDurationVO selectCountDuration(@Param("userId") Long userId, @Param("start") LocalDateTime start);

    /**
     * 各动作的历史最佳（当前口径：单组 weight×reps 最大，并列取最近）。
     * 现算而不读 workout_set.pr（那是「提交时点」的历史布尔），保证归档始终等于当前事实。
     */
    @Select("""
            SELECT * FROM (
                SELECT wb.exercise_id AS exerciseId, wb.exercise_name AS name,
                       ws.weight AS weight, ws.reps AS reps,
                       w.created_at AS performedAt,
                       ROW_NUMBER() OVER (PARTITION BY wb.exercise_id
                                          ORDER BY ws.weight * ws.reps DESC, w.created_at DESC, ws.set_index DESC) AS rn
                FROM workout_set ws
                JOIN workout_block wb ON wb.id = ws.block_id
                JOIN workout w ON w.id = wb.workout_id
                WHERE w.user_id = #{userId} AND wb.exercise_id IS NOT NULL
            ) t
            WHERE t.rn = 1
            ORDER BY t.performedAt DESC
            """)
    List<com.example.fitapp.module.stats.vo.StatsVO.PrItem> selectPrArchive(@Param("userId") Long userId);
}
