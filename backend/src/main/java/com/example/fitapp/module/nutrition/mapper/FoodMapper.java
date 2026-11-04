package com.example.fitapp.module.nutrition.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.fitapp.module.nutrition.entity.Food;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface FoodMapper extends BaseMapper<Food> {

    /** 浏览次数 +1（详情页查看时调用；原子自增，避免「读-改-写」竞态覆盖）。 */
    @Update("UPDATE food SET view_count = view_count + 1 WHERE id = #{id}")
    int incrementViewCount(@Param("id") Long id);

    /** 这个用户用过这条食物：饮食记录或食物组合引用过（ADR 0048 的「用过」）。 */
    @Select("""
            SELECT (EXISTS (SELECT 1 FROM meal_log WHERE user_id = #{userId} AND food_id = #{foodId})
                 OR EXISTS (SELECT 1 FROM food_combo_item ci JOIN food_combo fc ON fc.id = ci.combo_id
                            WHERE fc.user_id = #{userId} AND ci.food_id = #{foodId}))
            """)
    boolean usedBy(@Param("userId") Long userId, @Param("foodId") Long foodId);

    /** 预置食物现有的分类（管理端下拉用）。 */
    @Select("SELECT DISTINCT category FROM food WHERE user_id IS NULL AND category IS NOT NULL ORDER BY category")
    List<String> presetCategories();
}
