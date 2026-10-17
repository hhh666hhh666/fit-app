package com.example.fitapp.module.profile.service;

import com.example.fitapp.module.body.entity.BodyRecord;
import com.example.fitapp.module.body.mapper.BodyRecordMapper;
import com.example.fitapp.module.profile.entity.UserProfile;
import com.example.fitapp.module.profile.mapper.UserProfileMapper;
import com.example.fitapp.module.profile.vo.EnergyVO;
import com.example.fitapp.module.workout.mapper.WorkoutMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;

/**
 * 每日总消耗的计算**唯一实现**——饮食页与档案卡都走这里，绝不允许前端再算一遍（会两处漂开）。
 *
 * <p>口径（ADR 0029）：总额 ＝ 基础代谢（Mifflin-St Jeor）× 生活活动系数 ＋ 当日手填的训练消耗。
 * **生活活动系数只含日常生活，专门训练不在里面**——这是防重复计算的唯一机制，改系数取值前必须先读 ADR 0029。</p>
 *
 * <p>缺输入就不算：档案缺一项或没有体重记录时返回 `complete=false`，绝不用人群均值兜底
 * （那会产出「看着精确其实是编的」数字）。</p>
 */
@Service
@RequiredArgsConstructor
public class EnergyService {

    public static final String SEX_MALE = "male";

    /** 生活活动档：只含日常生活（工作性质与走动），**不含专门训练**。 */
    public static final String LEVEL_SEDENTARY = "sedentary";
    public static final String LEVEL_LIGHT = "light";
    public static final String LEVEL_ACTIVE = "active";

    /** 缺什么：个人档案没填全。 */
    public static final String MISSING_PROFILE = "profile";
    /** 缺什么：没有体重记录。 */
    public static final String MISSING_WEIGHT = "weight";

    /** 系数：久坐 1.2 ／ 经常走动 1.35 ／ 体力工作 1.5。专门训练由手填消耗单独加，见类注释。 */
    private static final BigDecimal FACTOR_SEDENTARY = new BigDecimal("1.20");
    private static final BigDecimal FACTOR_LIGHT = new BigDecimal("1.35");
    private static final BigDecimal FACTOR_ACTIVE = new BigDecimal("1.50");

    /** Mifflin-St Jeor 的性别常数项：男 +5、女 −161。 */
    private static final BigDecimal MALE_CONSTANT = new BigDecimal("5");
    private static final BigDecimal FEMALE_CONSTANT = new BigDecimal("-161");

    private final UserProfileMapper userProfileMapper;
    private final BodyRecordMapper bodyRecordMapper;
    private final WorkoutMapper workoutMapper;

    /** 某一天的每日总消耗拆解。 */
    public EnergyVO compute(Long userId, LocalDate date) {
        EnergyVO vo = new EnergyVO();
        vo.setDate(date);
        vo.setTrainingKcal(trainingKcal(userId, date));

        UserProfile p = userProfileMapper.selectById(userId);
        BodyRecord latest = bodyRecordMapper.selectLatestWithWeight(userId);
        if (p == null) {
            vo.setComplete(false);
            vo.setMissing(MISSING_PROFILE);
            return vo;
        }
        if (latest == null || latest.getWeight() == null) {
            vo.setComplete(false);
            vo.setMissing(MISSING_WEIGHT);
            return vo;
        }

        int age = ageOf(p.getBirthDate(), date);
        int bmr = bmr(p.getSex(), latest.getWeight(), p.getHeightCm(), age);
        BigDecimal factor = factorOf(p.getActivityLevel());
        int base = BigDecimal.valueOf(bmr).multiply(factor).setScale(0, RoundingMode.HALF_UP).intValue();

        vo.setComplete(true);
        vo.setSex(p.getSex());
        vo.setAge(age);
        vo.setHeightCm(p.getHeightCm());
        vo.setWeightKg(latest.getWeight());
        vo.setWeightAt(latest.getMeasuredAt());
        vo.setActivityLevel(p.getActivityLevel());
        vo.setActivityFactor(factor);
        vo.setBmr(bmr);
        vo.setBaseKcal(base);
        vo.setTotalKcal(base + (vo.getTrainingKcal() == null ? 0 : vo.getTrainingKcal()));
        return vo;
    }

    /** 年龄现算：按计算日与出生日期之差（存生日不存年龄，ADR 0030）。 */
    public int ageOf(LocalDate birthDate, LocalDate at) {
        return Period.between(birthDate, at).getYears();
    }

    /**
     * 基础代谢（Mifflin-St Jeor，取整大卡）：
     * 男 10×体重 + 6.25×身高 − 5×年龄 + 5；女 … − 161。
     */
    public int bmr(String sex, BigDecimal weightKg, BigDecimal heightCm, int age) {
        BigDecimal base = weightKg.multiply(BigDecimal.TEN)
                .add(heightCm.multiply(new BigDecimal("6.25")))
                .subtract(BigDecimal.valueOf(age).multiply(new BigDecimal("5")));
        BigDecimal constant = SEX_MALE.equals(sex) ? MALE_CONSTANT : FEMALE_CONSTANT;
        return base.add(constant).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    /** 档位 → 系数；未知档位按久坐处理（保守：宁可算少，不凭空多给热量）。 */
    private BigDecimal factorOf(String level) {
        if (LEVEL_LIGHT.equals(level)) {
            return FACTOR_LIGHT;
        }
        if (LEVEL_ACTIVE.equals(level)) {
            return FACTOR_ACTIVE;
        }
        return FACTOR_SEDENTARY;
    }

    /** 当日训练消耗：按 `workout.started_at` 归属（跨零点的训练算开始那天）。 */
    private Integer trainingKcal(Long userId, LocalDate date) {
        Integer sum = workoutMapper.selectBurnedBetween(userId,
                date.atStartOfDay(), date.plusDays(1).atStartOfDay());
        return sum == null ? 0 : sum;
    }
}
