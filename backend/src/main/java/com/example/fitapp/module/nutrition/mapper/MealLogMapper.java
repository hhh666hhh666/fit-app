package com.example.fitapp.module.nutrition.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.fitapp.module.nutrition.entity.MealLog;
import com.example.fitapp.module.nutrition.vo.DailyNutritionRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

public interface MealLogMapper extends BaseMapper<MealLog> {

    /**
     * 逐日营养合计（趋势用）：实际摄入 = per100 × grams ÷ 100，按日聚合。
     * 只返回有记录的日期；缺日在服务端补零。
     */
    @Select("""
            SELECT log_date,
                   SUM(kcal_per100 * grams / 100)    AS kcal,
                   SUM(protein_per100 * grams / 100) AS protein,
                   SUM(carb_per100 * grams / 100)    AS carb,
                   SUM(fat_per100 * grams / 100)     AS fat
            FROM meal_log
            WHERE user_id = #{userId} AND log_date BETWEEN #{from} AND #{to}
            GROUP BY log_date
            """)
    List<DailyNutritionRow> selectDailyTotals(@Param("userId") Long userId,
                                              @Param("from") LocalDate from,
                                              @Param("to") LocalDate to);

    /**
     * 该用户最早有数据的一天（`meal_log` 与 `diet_daily` 取更早者；都没有则 null）。
     * 用作趋势的默认起点——前端的「数据窗口＝全部历史」（ADR 0015）。
     */
    @Select("""
            SELECT MIN(d) FROM (
                SELECT MIN(log_date) AS d FROM meal_log WHERE user_id = #{userId}
                UNION ALL
                SELECT MIN(log_date) AS d FROM diet_daily WHERE user_id = #{userId}
            ) t
            """)
    LocalDate selectEarliestDate(@Param("userId") Long userId);
}
