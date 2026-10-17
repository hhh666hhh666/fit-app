package com.example.fitapp.module.profile.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 个人档案（身体档案页顶部那张卡用）：档案四项 ＋ 当前体重 ＋ 今日消耗概览。
 * 档案没填时为 `complete = false`，各字段为 null——前端据此换成「补全档案」入口。
 */
@Data
public class ProfileVO {

    /** 档案行是否存在（四项都已填）。 */
    private Boolean complete;

    private String sex;

    private LocalDate birthDate;

    /** 现算年龄（从出生日期算，不存数字）。 */
    private Integer age;

    private BigDecimal heightCm;

    private String activityLevel;

    /** 最近一条体重 kg（来自身体档案；没录过为 null）。 */
    private BigDecimal latestWeight;

    /** 今天的每日总消耗拆解；档案不全时为 complete=false 的空壳。 */
    private EnergyVO energy;
}
