package com.example.fitapp.module.nutrition.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.fitapp.module.nutrition.entity.Food;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface FoodMapper extends BaseMapper<Food> {

    /** 浏览次数 +1（详情页查看时调用；原子自增，避免「读-改-写」竞态覆盖）。 */
    @Update("UPDATE food SET view_count = view_count + 1 WHERE id = #{id}")
    int incrementViewCount(@Param("id") Long id);
}
