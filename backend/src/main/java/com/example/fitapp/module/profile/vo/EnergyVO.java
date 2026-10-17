package com.example.fitapp.module.profile.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 某一天的「每日总消耗」拆解——**同时充当「怎么算的」的说明数据**（ADR 0029）。
 *
 * <p>口径：总额 ＝ 基础代谢 × 生活活动系数 ＋ 当日手填的训练消耗。生活活动系数只含日常生活，
 * 训练不在里面，两者不重叠；这就是不重复计算的唯一机制。</p>
 *
 * <p>把代入的每个数都返回，是为了让主人能自己核对：数字不对时才能分清是「档位选错」还是「体重是旧的」。</p>
 */
@Data
public class EnergyVO {

    private LocalDate date;

    /** 能不能算（档案三项齐全 且 有体重记录）。false 时下列数值全为 null。 */
    private Boolean complete;

    /** 缺什么：`profile`＝个人档案没填全，`weight`＝没有体重记录；齐全时为 null。 */
    private String missing;

    private String sex;

    private Integer age;

    private BigDecimal heightCm;

    /** 参与计算的那条体重 kg。 */
    private BigDecimal weightKg;

    /** 那条体重的测定时刻（提示「体重是不是旧的」）。 */
    private LocalDateTime weightAt;

    private String activityLevel;

    /** 生活活动系数。 */
    private BigDecimal activityFactor;

    /** 基础代谢（Mifflin-St Jeor，取整大卡）。 */
    private Integer bmr;

    /** 基础消耗 ＝ 基础代谢 × 系数（取整）。 */
    private Integer baseKcal;

    /** 当日手填的训练消耗合计。 */
    private Integer trainingKcal;

    /** 每日总消耗 ＝ baseKcal ＋ trainingKcal。 */
    private Integer totalKcal;
}
