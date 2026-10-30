package com.example.fitapp.module.trash.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.fitapp.module.trash.entity.TrashItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface TrashItemMapper extends BaseMapper<TrashItem> {

    /** 回收站里照片的总占用（配额要算上它们，ADR 0047）。 */
    @Select("SELECT COALESCE(SUM(photo_bytes), 0) FROM trash_item WHERE user_id = #{userId}")
    long sumPhotoBytes(@Param("userId") Long userId);
}
