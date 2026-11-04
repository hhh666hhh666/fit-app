package com.example.fitapp.module.preset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.fitapp.module.preset.entity.PresetHidden;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface PresetHiddenMapper extends BaseMapper<PresetHidden> {

    /** 隐藏；已经隐藏过就什么都不做（唯一键兜底，重复点删除不报错）。 */
    @Insert("INSERT IGNORE INTO preset_hidden (user_id, kind, ref_id) VALUES (#{userId}, #{kind}, #{refId})")
    int hide(@Param("userId") Long userId, @Param("kind") String kind, @Param("refId") Long refId);
}
