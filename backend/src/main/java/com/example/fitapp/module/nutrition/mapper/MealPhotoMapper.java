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
}
