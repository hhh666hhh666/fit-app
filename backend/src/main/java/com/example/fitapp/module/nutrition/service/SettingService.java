package com.example.fitapp.module.nutrition.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.fitapp.module.nutrition.dto.SettingDTO;
import com.example.fitapp.module.nutrition.entity.UserSetting;
import com.example.fitapp.module.nutrition.mapper.UserSettingMapper;
import com.example.fitapp.module.nutrition.vo.SettingVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 饮食设置与目标（每用户一行；行不存在时读取按默认值呈现，首次写入才落行）。
 */
@Service
@RequiredArgsConstructor
public class SettingService {

    public static final String DEFAULT_PHOTO_QUALITY = "medium";
    public static final int DEFAULT_WATER_STEP_ML = 250;

    private final UserSettingMapper userSettingMapper;

    public SettingVO get(Long userId) {
        UserSetting s = userSettingMapper.selectOne(Wrappers.<UserSetting>lambdaQuery()
                .eq(UserSetting::getUserId, userId));
        SettingVO vo = new SettingVO();
        if (s == null) {
            vo.setPhotoQuality(DEFAULT_PHOTO_QUALITY);
            vo.setWaterStepMl(DEFAULT_WATER_STEP_ML);
            return vo;
        }
        vo.setPhotoQuality(s.getPhotoQuality());
        vo.setWaterStepMl(s.getWaterStepMl());
        vo.setTargetKcal(s.getTargetKcal());
        vo.setTargetProtein(s.getTargetProtein());
        vo.setTargetCarb(s.getTargetCarb());
        vo.setTargetFat(s.getTargetFat());
        vo.setTargetWater(s.getTargetWater());
        return vo;
    }

    /** 全量提交：压缩等级与饮水步进必填；目标传 null = 清除。 */
    public SettingVO update(Long userId, SettingDTO dto) {
        UserSetting existing = userSettingMapper.selectOne(Wrappers.<UserSetting>lambdaQuery()
                .eq(UserSetting::getUserId, userId));
        if (existing == null) {
            UserSetting s = new UserSetting();
            s.setUserId(userId);
            s.setPhotoQuality(dto.getPhotoQuality());
            s.setWaterStepMl(dto.getWaterStepMl());
            s.setTargetKcal(dto.getTargetKcal());
            s.setTargetProtein(dto.getTargetProtein());
            s.setTargetCarb(dto.getTargetCarb());
            s.setTargetFat(dto.getTargetFat());
            s.setTargetWater(dto.getTargetWater());
            userSettingMapper.insert(s);
        } else {
            // ⚠️ 目标可清空（传 null）——全局 update-strategy=not_null 会跳过 null，必须显式 set。
            userSettingMapper.update(null, Wrappers.<UserSetting>lambdaUpdate()
                    .eq(UserSetting::getUserId, userId)
                    .set(UserSetting::getPhotoQuality, dto.getPhotoQuality())
                    .set(UserSetting::getWaterStepMl, dto.getWaterStepMl())
                    .set(UserSetting::getTargetKcal, dto.getTargetKcal())
                    .set(UserSetting::getTargetProtein, dto.getTargetProtein())
                    .set(UserSetting::getTargetCarb, dto.getTargetCarb())
                    .set(UserSetting::getTargetFat, dto.getTargetFat())
                    .set(UserSetting::getTargetWater, dto.getTargetWater()));
        }
        return get(userId);
    }
}
