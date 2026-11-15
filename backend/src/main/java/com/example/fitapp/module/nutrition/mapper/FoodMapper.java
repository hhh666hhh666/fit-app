package com.example.fitapp.module.nutrition.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.example.fitapp.module.nutrition.entity.Food;
import com.example.fitapp.module.nutrition.service.FoodSearch;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;

public interface FoodMapper extends BaseMapper<Food> {

    /**
     * 食物库列表与搜索（ADR 0056）。筛选条件由调用方放进 wrapper（可见范围、关键词），这里只管排序和分页：
     * ① 有关键词时先按匹配度分档（名字、名字方括号里的叫法、每个别名，取最好的一档；见 FoodSearch.SEGMENTS）
     * ② 收藏 ③ 这个用户从 since 起记过几次 ④ 自建排在预置前面 ⑤ 名称、id（分页稳定）。
     * wrapper 里不要放 orderBy——ORDER BY 在这里写死。
     */
    @ResultMap("mybatis-plus_Food")
    @Select("""
            <script>
            SELECT food.* FROM food ${ew.customSqlSegment}
            ORDER BY
            <if test="t != null">
              CASE
                WHEN food.name = #{t.kw} OR ${@com.example.fitapp.module.nutrition.service.FoodSearch@SEGMENTS} LIKE #{t.exactAlias} ESCAPE '!' THEN 0
                WHEN food.name LIKE #{t.prefix} ESCAPE '!' OR ${@com.example.fitapp.module.nutrition.service.FoodSearch@SEGMENTS} LIKE #{t.prefixAlias} ESCAPE '!' THEN 1
                WHEN food.name LIKE #{t.contains} ESCAPE '!' OR food.aliases LIKE #{t.contains} ESCAPE '!' THEN 2
                ELSE 3
              END,
            </if>
              food.favorite DESC,
              (SELECT COUNT(*) FROM meal_log ml
                WHERE ml.user_id = #{userId} AND ml.food_id = food.id AND ml.log_date &gt;= #{since}) DESC,
              (food.user_id IS NULL),
              food.name,
              food.id
            <if test="limit != null">LIMIT #{offset}, #{limit}</if>
            </script>
            """)
    List<Food> search(@Param(Constants.WRAPPER) Wrapper<Food> ew,
                      @Param("t") FoodSearch t,
                      @Param("userId") Long userId,
                      @Param("since") LocalDate since,
                      @Param("offset") Integer offset,
                      @Param("limit") Integer limit);

    /** 这个用户自建食物的图片一共占多少字节（照片空间要算上，ADR 0057）。 */
    @Select("SELECT COALESCE(SUM(image_bytes), 0) FROM food WHERE user_id = #{userId}")
    long sumImageBytes(@Param("userId") Long userId);

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
