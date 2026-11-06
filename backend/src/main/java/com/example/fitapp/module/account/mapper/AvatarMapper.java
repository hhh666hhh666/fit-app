package com.example.fitapp.module.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.fitapp.module.account.entity.Avatar;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AvatarMapper extends BaseMapper<Avatar> {

    /** 头像总占用（原图＋缩略图＋预览图＋裁剪结果），和餐照、日记图片共用一个上限（ADR 0051）。 */
    @Select("""
            SELECT COALESCE(SUM(size_bytes + extra_bytes), 0)
            FROM avatar WHERE user_id = #{userId}
            """)
    long sumBytes(@Param("userId") Long userId);
}
