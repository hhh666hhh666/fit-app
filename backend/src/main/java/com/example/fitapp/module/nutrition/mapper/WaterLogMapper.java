package com.example.fitapp.module.nutrition.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.fitapp.module.nutrition.entity.WaterLog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

public interface WaterLogMapper extends BaseMapper<WaterLog> {

    /** 当天各笔之和（没有就是 0）。 */
    @Select("SELECT COALESCE(SUM(ml), 0) FROM water_log WHERE user_id = #{userId} AND log_date = #{date}")
    int sumOfDay(@Param("userId") Long userId, @Param("date") LocalDate date);
}
