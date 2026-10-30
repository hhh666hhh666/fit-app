package com.example.fitapp.module.diary.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.fitapp.module.diary.entity.DiaryPhoto;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface DiaryPhotoMapper extends BaseMapper<DiaryPhoto> {

    /** 日记图片总占用（原图 ＋ 缩略图字节数），和餐照共用一个上限（ADR 0044）。 */
    @Select("""
            SELECT COALESCE(SUM(size_bytes + COALESCE(thumb_bytes, 0)), 0)
            FROM diary_photo WHERE user_id = #{userId}
            """)
    long sumBytes(@Param("userId") Long userId);

    /** 日记图片张数。 */
    @Select("SELECT COUNT(*) FROM diary_photo WHERE user_id = #{userId}")
    long countByUser(@Param("userId") Long userId);
}
