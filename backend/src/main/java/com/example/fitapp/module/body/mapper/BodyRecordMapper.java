package com.example.fitapp.module.body.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.fitapp.module.body.entity.BodyRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

public interface BodyRecordMapper extends BaseMapper<BodyRecord> {

    /**
     * 最近一条**有体重**的测定——「我的」页与档案展示最新体重用。
     * 只量了体脂或围度的记录不能当体重用，所以必须带 `weight IS NOT NULL`。
     */
    @Select("""
            SELECT * FROM body_record
            WHERE user_id = #{userId} AND weight IS NOT NULL
            ORDER BY measured_at DESC, id DESC
            LIMIT 1
            """)
    BodyRecord selectLatestWithWeight(@Param("userId") Long userId);

    /**
     * `before` 之前最后一条**有体重**的测定——某天的每日总消耗拿它算基础代谢（ADR 0042）。
     * 传次日 0 点，就是「那天结束前最后一次测得的体重」。
     */
    @Select("""
            SELECT * FROM body_record
            WHERE user_id = #{userId} AND weight IS NOT NULL AND measured_at < #{before}
            ORDER BY measured_at DESC, id DESC
            LIMIT 1
            """)
    BodyRecord selectLastWithWeightBefore(@Param("userId") Long userId, @Param("before") LocalDateTime before);
}
