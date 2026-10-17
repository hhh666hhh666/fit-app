package com.example.fitapp.module.workout.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.fitapp.module.workout.entity.Workout;
import com.example.fitapp.module.workout.vo.CountDurationVO;
import com.example.fitapp.module.workout.vo.CountVolumeVO;
import com.example.fitapp.module.workout.vo.ExerciseMaxVO;
import com.example.fitapp.module.workout.vo.LastRecordVO;
import com.example.fitapp.module.workout.vo.WorkoutListItemVO;
import com.example.fitapp.module.workout.vo.WorkoutTotalsVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface WorkoutMapper extends BaseMapper<Workout> {

    /**
     * 日志列表：每条带「第一个动作名」概要与**层数合计**，按时间倒序。
     * `start`／`end` 非空时按月过滤（左闭右开）；`offset` 供触底分页。
     *
     * <p>成绩位口径（ADR 0025）：`hasStrength` 为真看容量，否则看 `totalFloors`，
     * 两者都空才是「—」——纯有氧场次不许显示「0 kg」那种像故障的文案。</p>
     */
    @Select("""
            <script>
            SELECT w.id, w.title, w.duration_sec AS durationSec, w.total_volume AS totalVolume,
                   w.created_at AS createdAt,
                   (SELECT wb.exercise_name FROM workout_block wb
                    WHERE wb.workout_id = w.id ORDER BY wb.sort_order LIMIT 1) AS firstExercise,
                   EXISTS (SELECT 1 FROM workout_block wb2
                           LEFT JOIN exercise e2 ON e2.id = wb2.exercise_id
                           WHERE wb2.workout_id = w.id
                             AND (e2.muscle IS NULL OR e2.muscle &lt;&gt; 'cardio')) AS hasStrength,
                   (SELECT COALESCE(SUM(ws2.floors), 0) FROM workout_set ws2
                    JOIN workout_block wb3 ON wb3.id = ws2.block_id
                    WHERE wb3.workout_id = w.id) AS totalFloors
            FROM workout w
            WHERE w.user_id = #{userId}
            <if test="start != null"> AND w.created_at &gt;= #{start} AND w.created_at &lt; #{end} </if>
            ORDER BY w.created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<WorkoutListItemVO> selectListItems(@Param("userId") Long userId,
                                            @Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end,
                                            @Param("limit") int limit,
                                            @Param("offset") int offset);

    /** 某动作的「上次数据」：最近一次训练里该动作的最后一组。 */
    @Select("""
            SELECT ws.weight, ws.reps, ws.floors, w.created_at AS performedAt
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

    /**
     * 这些动作的历史「最重一组」——PR 判定的历史起点（ADR 0024：重量优先，同重量比次数）。
     * 并列时取最近的一次（值相同，取哪条对判定无影响）。
     */
    @Select("""
            <script>
            SELECT t.exerciseId, t.weight, t.reps FROM (
                SELECT wb.exercise_id AS exerciseId, ws.weight AS weight, ws.reps AS reps,
                       ROW_NUMBER() OVER (PARTITION BY wb.exercise_id
                                          ORDER BY ws.weight DESC, ws.reps DESC,
                                                   w.created_at DESC, ws.set_index DESC) AS rn
                FROM workout_set ws
                JOIN workout_block wb ON wb.id = ws.block_id
                JOIN workout w ON w.id = wb.workout_id
                WHERE w.user_id = #{userId} AND wb.exercise_id IN
                <foreach collection="exerciseIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            ) t
            WHERE t.rn = 1
            </script>
            """)
    List<ExerciseMaxVO> selectMaxSetVolume(@Param("userId") Long userId,
                                           @Param("exerciseIds") Collection<Long> exerciseIds);

    /**
     * 同上，但排除某次训练——编辑已有记录时用它取「不含本记录」的历史基线，
     * 否则把重量改大后会拿自己当历史来比，PR 判定失真。
     */
    @Select("""
            <script>
            SELECT t.exerciseId, t.weight, t.reps FROM (
                SELECT wb.exercise_id AS exerciseId, ws.weight AS weight, ws.reps AS reps,
                       ROW_NUMBER() OVER (PARTITION BY wb.exercise_id
                                          ORDER BY ws.weight DESC, ws.reps DESC,
                                                   w.created_at DESC, ws.set_index DESC) AS rn
                FROM workout_set ws
                JOIN workout_block wb ON wb.id = ws.block_id
                JOIN workout w ON w.id = wb.workout_id
                WHERE w.user_id = #{userId} AND w.id &lt;&gt; #{excludeWorkoutId} AND wb.exercise_id IN
                <foreach collection="exerciseIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            ) t
            WHERE t.rn = 1
            </script>
            """)
    List<ExerciseMaxVO> selectMaxSetVolumeExcluding(@Param("userId") Long userId,
                                                    @Param("exerciseIds") Collection<Long> exerciseIds,
                                                    @Param("excludeWorkoutId") Long excludeWorkoutId);

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

    /** 区间内的训练次数与总容量（左闭右开）——日志页按月概览与环比用。 */
    @Select("""
            SELECT COUNT(*) AS cnt, COALESCE(SUM(w.total_volume), 0) AS vol
            FROM workout w
            WHERE w.user_id = #{userId} AND w.created_at >= #{start} AND w.created_at < #{end}
            """)
    CountVolumeVO selectCountVolume(@Param("userId") Long userId,
                                    @Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end);

    /**
     * 各动作的历史最佳（ADR 0024 口径：**重量优先，同重量比次数**，并列取最近）。
     * 现算而不读 workout_set.pr（那是「提交时点」的历史布尔），保证归档始终等于当前事实；
     * **有氧动作不参与**（ADR 0023／0024：其「重量」没有可比性）。
     *
     * <p>⚠️ 本段 SQL 不带 {@code <script>}，MyBatis 原样发给 MySQL、不做 XML 解析：
     * 这里必须写裸的 {@code <>}，写成 {@code &lt;&gt;} 会变成字面量、直接语法错误。</p>
     */
    @Select("""
            SELECT * FROM (
                SELECT wb.exercise_id AS exerciseId, wb.exercise_name AS name,
                       ws.weight AS weight, ws.reps AS reps,
                       w.created_at AS performedAt,
                       ROW_NUMBER() OVER (PARTITION BY wb.exercise_id
                                          ORDER BY ws.weight DESC, ws.reps DESC,
                                                   w.created_at DESC, ws.set_index DESC) AS rn
                FROM workout_set ws
                JOIN workout_block wb ON wb.id = ws.block_id
                JOIN workout w ON w.id = wb.workout_id
                JOIN exercise e ON e.id = wb.exercise_id
                WHERE w.user_id = #{userId} AND wb.exercise_id IS NOT NULL AND e.muscle <> 'cardio'
            ) t
            WHERE t.rn = 1
            ORDER BY t.performedAt DESC
            """)
    List<com.example.fitapp.module.stats.vo.StatsVO.PrItem> selectPrArchive(@Param("userId") Long userId);

    /** 「我的」页累计口径：全量训练次数与总容量（不再拿封顶列表求和冒充「累计」，ADR 0023）。 */
    @Select("""
            SELECT COUNT(*) AS `count`, COALESCE(SUM(w.total_volume), 0) AS totalVolume
            FROM workout w
            WHERE w.user_id = #{userId}
            """)
    WorkoutTotalsVO selectTotals(@Param("userId") Long userId);
}
