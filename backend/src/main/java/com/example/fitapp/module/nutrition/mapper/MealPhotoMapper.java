package com.example.fitapp.module.nutrition.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.fitapp.module.nutrition.entity.MealPhoto;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;

public interface MealPhotoMapper extends BaseMapper<MealPhoto> {

    /** 指定日各餐的照片存在性（**不取 BLOB**）。 */
    @Select("""
            SELECT DISTINCT meal FROM meal_photo
            WHERE user_id = #{userId} AND log_date = #{date}
            """)
    List<String> selectMealsWithPhoto(@Param("userId") Long userId, @Param("date") LocalDate date);

    /**
     * 照片总占用（原图 ＋ 缩略图字节数）。
     * ⚠️ 图片已迁到文件系统，缩略图不再存在库里——所以按 {@code thumb_bytes}（迁移时记下的实测值）
     * 统计，而不是 {@code LENGTH(thumb)}（那个字段现在是 NULL）。
     */
    @Select("""
            SELECT COALESCE(SUM(size_bytes + COALESCE(thumb_bytes, 0)), 0)
            FROM meal_photo WHERE user_id = #{userId}
            """)
    long sumBytes(@Param("userId") Long userId);

    /** 照片张数。 */
    @Select("SELECT COUNT(*) FROM meal_photo WHERE user_id = #{userId}")
    long countByUser(@Param("userId") Long userId);

    /**
     * 相册列表：按日期倒序、同日按早→晚（**不取 BLOB**，只出元数据）。
     * 缩略图由前端逐个走 `/api/diet/photos/{id}/thumb` 取。
     */
    @Select("""
            SELECT id, user_id, log_date, meal, mime, size_bytes, path, thumb_bytes, created_at, updated_at
            FROM meal_photo
            WHERE user_id = #{userId}
            ORDER BY log_date DESC, FIELD(meal, 'breakfast', 'lunch', 'dinner', 'snack'), id
            LIMIT #{offset}, #{limit}
            """)
    List<MealPhoto> selectAlbum(@Param("userId") Long userId,
                               @Param("offset") int offset,
                               @Param("limit") int limit);

    /** 还没搬走的行（图片数据仍在库里）——搬运器用。 */
    @Select("""
            SELECT id, user_id, log_date, meal, mime, size_bytes, image, thumb
            FROM meal_photo
            WHERE image IS NOT NULL
            ORDER BY id
            """)
    List<MealPhoto> selectPendingMigration();

    /**
     * 落盘成功后的收尾：回填 path/thumb_bytes，并把两个 BLOB 列置 NULL（库体积的实际收益在这里）。
     * <p>⚠️ 必须用自定义 SQL——MyBatis-Plus 的 {@code updateById} 默认忽略 null 字段，置不空这两列。
     */
    @Update("""
            UPDATE meal_photo
            SET path = #{path}, thumb_bytes = #{thumbBytes}, image = NULL, thumb = NULL
            WHERE id = #{id}
            """)
    int markMigrated(@Param("id") Long id,
                     @Param("path") String path,
                     @Param("thumbBytes") Integer thumbBytes);
}
