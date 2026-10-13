package com.example.fitapp.module.nutrition.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.fitapp.module.nutrition.entity.MealPhoto;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

public interface MealPhotoMapper extends BaseMapper<MealPhoto> {

    /** 指定日各餐的照片存在性（**不取 BLOB**）。 */
    @Select("""
            SELECT meal FROM meal_photo
            WHERE user_id = #{userId} AND log_date = #{date}
            """)
    List<String> selectMealsWithPhoto(@Param("userId") Long userId, @Param("date") LocalDate date);

    /** 照片总占用（原图 ＋ 缩略图字节数）。 */
    @Select("""
            SELECT COALESCE(SUM(size_bytes + COALESCE(LENGTH(thumb), 0)), 0)
            FROM meal_photo WHERE user_id = #{userId}
            """)
    long sumBytes(@Param("userId") Long userId);

    /** 照片张数。 */
    @Select("SELECT COUNT(*) FROM meal_photo WHERE user_id = #{userId}")
    long countByUser(@Param("userId") Long userId);

    /**
     * 相册列表：按日期倒序、同日按早→晚（**不取 BLOB**，只出元数据）。
     * 缩略图由前端逐个走 `/api/diet/photos/{date}/{meal}/thumb` 取。
     */
    @Select("""
            SELECT id, user_id, log_date, meal, mime, size_bytes, created_at, updated_at
            FROM meal_photo
            WHERE user_id = #{userId}
            ORDER BY log_date DESC, FIELD(meal, 'breakfast', 'lunch', 'dinner', 'snack')
            LIMIT #{offset}, #{limit}
            """)
    List<MealPhoto> selectAlbum(@Param("userId") Long userId,
                               @Param("offset") int offset,
                               @Param("limit") int limit);
}
