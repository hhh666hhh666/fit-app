package com.example.fitapp.module.profile.service;

import com.example.fitapp.module.body.entity.BodyRecord;
import com.example.fitapp.module.body.mapper.BodyRecordMapper;
import com.example.fitapp.module.profile.dto.ProfileSaveDTO;
import com.example.fitapp.module.profile.entity.UserProfile;
import com.example.fitapp.module.profile.mapper.UserProfileMapper;
import com.example.fitapp.module.profile.vo.ProfileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 个人档案读写：一个用户一份，保存即整体覆盖（四项一起提交）。
 * 消耗概览不在这里算，交给 {@link EnergyService}——饮食页读的是同一份实现。
 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserProfileMapper userProfileMapper;
    private final BodyRecordMapper bodyRecordMapper;
    private final EnergyService energyService;

    public ProfileVO get(Long userId) {
        UserProfile p = userProfileMapper.selectById(userId);
        ProfileVO vo = new ProfileVO();
        vo.setComplete(p != null);
        if (p != null) {
            vo.setSex(p.getSex());
            vo.setBirthDate(p.getBirthDate());
            vo.setAge(energyService.ageOf(p.getBirthDate(), LocalDate.now()));
            vo.setHeightCm(p.getHeightCm());
            vo.setActivityLevel(p.getActivityLevel());
        }
        BodyRecord latest = bodyRecordMapper.selectLatestWithWeight(userId);
        vo.setLatestWeight(latest == null ? null : latest.getWeight());
        vo.setEnergy(energyService.compute(userId, LocalDate.now()));
        return vo;
    }

    /** 新增或覆盖（档案没有「部分更新」：四项缺一项就算不出消耗）。 */
    @Transactional
    public ProfileVO save(Long userId, ProfileSaveDTO dto) {
        UserProfile p = userProfileMapper.selectById(userId);
        boolean exists = p != null;
        if (!exists) {
            p = new UserProfile();
            p.setUserId(userId);
        }
        p.setSex(dto.getSex());
        p.setBirthDate(dto.getBirthDate());
        p.setHeightCm(dto.getHeightCm());
        p.setActivityLevel(dto.getActivityLevel());
        if (exists) {
            userProfileMapper.updateById(p);
        } else {
            userProfileMapper.insert(p);
        }
        return get(userId);
    }
}
