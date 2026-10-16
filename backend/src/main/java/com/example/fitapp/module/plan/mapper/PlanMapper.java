package com.example.fitapp.module.plan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.fitapp.module.plan.entity.Plan;
import com.example.fitapp.module.plan.vo.PlanListItemVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface PlanMapper extends BaseMapper<Plan> {

    /**
     * 计划列表卡数据：动作计数 ＋「上次练该计划」的时间与总容量。
     * 上次两条子查询同条件（最近一条 workout），以 plan_id 弱引用为准——计划改名不影响。
     */
    @Select("""
            SELECT p.id, p.name,
                   (SELECT COUNT(*) FROM plan_exercise pe WHERE pe.plan_id = p.id) AS exerciseCount,
                   (SELECT w.created_at FROM workout w
                     WHERE w.user_id = p.user_id AND w.plan_id = p.id
                     ORDER BY w.created_at DESC, w.id DESC LIMIT 1) AS lastTrainedAt,
                   (SELECT w.total_volume FROM workout w
                     WHERE w.user_id = p.user_id AND w.plan_id = p.id
                     ORDER BY w.created_at DESC, w.id DESC LIMIT 1) AS lastVolume,
                   (SELECT COUNT(*) FROM workout_block wb2
                            LEFT JOIN exercise e2 ON e2.id = wb2.exercise_id
                     WHERE wb2.workout_id = (SELECT w.id FROM workout w
                                              WHERE w.user_id = p.user_id AND w.plan_id = p.id
                                              ORDER BY w.created_at DESC, w.id DESC LIMIT 1)
                       AND (e2.muscle IS NULL OR e2.muscle &lt;&gt; 'cardio')) AS lastHasStrength
            FROM plan p
            WHERE p.user_id = #{userId}
            ORDER BY p.id
            """)
    List<PlanListItemVO> selectListItems(@Param("userId") Long userId);
}
