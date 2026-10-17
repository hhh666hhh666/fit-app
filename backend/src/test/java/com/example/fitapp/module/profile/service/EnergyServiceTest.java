package com.example.fitapp.module.profile.service;

import com.example.fitapp.module.body.entity.BodyRecord;
import com.example.fitapp.module.body.mapper.BodyRecordMapper;
import com.example.fitapp.module.profile.entity.UserProfile;
import com.example.fitapp.module.profile.mapper.UserProfileMapper;
import com.example.fitapp.module.profile.vo.EnergyVO;
import com.example.fitapp.module.workout.mapper.WorkoutMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 纯单元测试（不碰库）：专测静默错误——
 * 公式性别常数项搞反、活动系数用到含训练的档（会双算）、档案缺项却硬算、
 * 训练消耗被系数放大、跨日期的消耗被算进今天。
 */
@ExtendWith(MockitoExtension.class)
class EnergyServiceTest {

    @Mock
    private UserProfileMapper userProfileMapper;

    @Mock
    private BodyRecordMapper bodyRecordMapper;

    @Mock
    private WorkoutMapper workoutMapper;

    private EnergyService service() {
        return new EnergyService(userProfileMapper, bodyRecordMapper, workoutMapper);
    }

    private static UserProfile profile(String sex, String level, String heightCm, LocalDate birth) {
        UserProfile p = new UserProfile();
        p.setUserId(7L);
        p.setSex(sex);
        p.setActivityLevel(level);
        p.setHeightCm(new BigDecimal(heightCm));
        p.setBirthDate(birth);
        return p;
    }

    private static BodyRecord weight(String kg) {
        BodyRecord r = new BodyRecord();
        r.setUserId(7L);
        r.setWeight(new BigDecimal(kg));
        r.setMeasuredAt(LocalDateTime.of(2026, 10, 1, 8, 0));
        return r;
    }

    @Test
    void male_bmrUsesMaleConstantAndFactorAppliesOnlyToBase() {
        LocalDate today = LocalDate.of(2026, 10, 17);
        // 30 岁：1996-10-17
        when(userProfileMapper.selectById(7L)).thenReturn(
                profile("male", "light", "175", LocalDate.of(1996, 10, 17)));
        when(bodyRecordMapper.selectLatestWithWeight(7L)).thenReturn(weight("70"));
        when(workoutMapper.selectBurnedBetween(eq(7L), any(), any())).thenReturn(320);

        EnergyVO vo = service().compute(7L, today);

        assertTrue(vo.getComplete());
        assertEquals(30, vo.getAge());
        // 10×70 + 6.25×175 − 5×30 + 5 = 1648.75 → 1649
        assertEquals(1649, vo.getBmr());
        // 1649 × 1.35 = 2226.15 → 2226（系数只放大基础代谢）
        assertEquals(2226, vo.getBaseKcal());
        assertEquals(320, vo.getTrainingKcal());
        // 训练消耗**加**上去，不再被系数放大——放大就是双算
        assertEquals(2546, vo.getTotalKcal());
    }

    @Test
    void female_bmrUsesFemaleConstant() {
        LocalDate today = LocalDate.of(2026, 10, 17);
        when(userProfileMapper.selectById(7L)).thenReturn(
                profile("female", "sedentary", "160", LocalDate.of(1996, 10, 17)));
        when(bodyRecordMapper.selectLatestWithWeight(7L)).thenReturn(weight("55"));
        when(workoutMapper.selectBurnedBetween(eq(7L), any(), any())).thenReturn(0);

        EnergyVO vo = service().compute(7L, today);

        // 10×55 + 6.25×160 − 5×30 − 161 = 550 + 1000 − 150 − 161 = 1239
        assertEquals(1239, vo.getBmr());
        // 1239 × 1.20 = 1486.8 → 1487
        assertEquals(1487, vo.getBaseKcal());
        assertEquals(1487, vo.getTotalKcal());
    }

    @Test
    void missingProfile_doesNotGuess() {
        when(userProfileMapper.selectById(7L)).thenReturn(null);
        when(bodyRecordMapper.selectLatestWithWeight(7L)).thenReturn(weight("70"));
        when(workoutMapper.selectBurnedBetween(eq(7L), any(), any())).thenReturn(100);

        EnergyVO vo = service().compute(7L, LocalDate.of(2026, 10, 17));

        assertFalse(vo.getComplete());
        assertEquals(EnergyService.MISSING_PROFILE, vo.getMissing());
        // 缺输入就不算：绝不用人群均值兜底凑一个数字出来
        assertNull(vo.getBmr());
        assertNull(vo.getTotalKcal());
    }

    @Test
    void missingWeight_doesNotGuess() {
        when(userProfileMapper.selectById(7L)).thenReturn(
                profile("male", "light", "175", LocalDate.of(1996, 10, 17)));
        when(bodyRecordMapper.selectLatestWithWeight(7L)).thenReturn(null);
        when(workoutMapper.selectBurnedBetween(eq(7L), any(), any())).thenReturn(0);

        EnergyVO vo = service().compute(7L, LocalDate.of(2026, 10, 17));

        assertFalse(vo.getComplete());
        assertEquals(EnergyService.MISSING_WEIGHT, vo.getMissing());
        assertNull(vo.getBaseKcal());
    }

    @Test
    void trainingKcal_queriedForRequestedDayOnly() {
        LocalDate day = LocalDate.of(2026, 10, 10);
        when(userProfileMapper.selectById(7L)).thenReturn(
                profile("male", "light", "175", LocalDate.of(1996, 10, 17)));
        when(bodyRecordMapper.selectLatestWithWeight(7L)).thenReturn(weight("70"));
        when(workoutMapper.selectBurnedBetween(7L, day.atStartOfDay(), day.plusDays(1).atStartOfDay()))
                .thenReturn(250);

        EnergyVO vo = service().compute(7L, day);

        // 区间必须正好是 [当天 0 点, 次日 0 点)：跨日的消耗不许混进来
        assertEquals(250, vo.getTrainingKcal());
    }
}
