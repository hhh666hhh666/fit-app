package com.example.fitapp.module.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.fitapp.module.admin.vo.AdminUserVO;
import com.example.fitapp.module.user.entity.User;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 管理端的用户统计（只读）。用户数是个位数，逐个子查询足够。 */
public interface AdminUserMapper extends BaseMapper<User> {

    @Select("""
            SELECT u.id, u.username, u.admin, u.created_at,
                   (SELECT COUNT(*) FROM workout w WHERE w.user_id = u.id) AS workout_count,
                   (SELECT COUNT(*) FROM meal_log m WHERE m.user_id = u.id) AS meal_count,
                   NULLIF(GREATEST(
                       COALESCE((SELECT MAX(w.started_at) FROM workout w WHERE w.user_id = u.id), CAST('1000-01-01' AS DATETIME)),
                       COALESCE((SELECT MAX(m.created_at) FROM meal_log m WHERE m.user_id = u.id), CAST('1000-01-01' AS DATETIME))),
                       CAST('1000-01-01' AS DATETIME)) AS last_record_at
              FROM user u
             ORDER BY u.id
            """)
    List<AdminUserVO> listWithStats();
}
