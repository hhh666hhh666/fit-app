package com.example.fitapp.module.nutrition.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.fitapp.module.nutrition.entity.DietDaily;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

public interface DietDailyMapper extends BaseMapper<DietDaily> {

    /** 窗口内的饮水行（趋势用；只取有行的日期）。 */
    @Select("""
            SELECT log_date, water_ml FROM diet_daily
            WHERE user_id = #{userId} AND log_date BETWEEN #{from} AND #{to}
            """)
    List<DietDaily> selectRange(@Param("userId") Long userId,
                                @Param("from") LocalDate from,
                                @Param("to") LocalDate to);
}
