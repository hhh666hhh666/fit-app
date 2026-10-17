package com.example.fitapp.module.body.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.fitapp.module.body.entity.BodyRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface BodyRecordMapper extends BaseMapper<BodyRecord> {

    /**
     * 最近一条**有体重**的测定——每日总消耗拿它算基础代谢（ADR 0029）。
     * 只量了体脂或围度的记录不能当体重用，所以必须带 `weight IS NOT NULL`。
     */
    @Select("""
            SELECT * FROM body_record
            WHERE user_id = #{userId} AND weight IS NOT NULL
            ORDER BY measured_at DESC, id DESC
            LIMIT 1
            """)
    BodyRecord selectLatestWithWeight(@Param("userId") Long userId);
}
