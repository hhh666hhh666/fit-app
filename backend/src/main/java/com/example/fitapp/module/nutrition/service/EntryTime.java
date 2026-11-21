package com.example.fitapp.module.nutrition.service;

import com.example.fitapp.common.BusinessException;
import com.example.fitapp.common.ResultCode;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 条目和饮水的「时刻」（ADR 0059）：几点几分吃喝的，只到分；接口里一律写 {@code HH:mm}。
 *
 * <p>没给时刻时的默认值：记今天，用现在的钟点；补记别的日子，早餐 8:00、午餐 12:00、晚餐 18:00，
 * 加餐和饮水用现在的钟点。前端打开弹层时按同一规则预填，这里只给不传时刻的老调用兜底。</p>
 */
public final class EntryTime {

    /** DTO 校验用：00:00–23:59。 */
    public static final String PATTERN = "([01]\\d|2[0-3]):[0-5]\\d";

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final Map<String, LocalTime> MEAL_DEFAULT = Map.of(
            "breakfast", LocalTime.of(8, 0),
            "lunch", LocalTime.of(12, 0),
            "dinner", LocalTime.of(18, 0));

    private EntryTime() {
    }

    /** 给了就解析，没给就按默认规则；{@code meal} 为 null 表示饮水。 */
    public static LocalTime resolve(String time, LocalDate date, String meal) {
        if (time != null && !time.isBlank()) {
            return parse(time);
        }
        LocalTime now = LocalTime.now(ZONE).withSecond(0).withNano(0);
        if (date.equals(LocalDate.now(ZONE)) || meal == null) {
            return now;
        }
        return MEAL_DEFAULT.getOrDefault(meal, now);
    }

    public static LocalTime parse(String time) {
        if (time == null || !time.matches(PATTERN)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "时刻格式不正确");
        }
        return LocalTime.parse(time, HH_MM);
    }

    /** {@code HH:mm}；null 原样返回（老条目没有时刻）。 */
    public static String format(LocalTime t) {
        return t == null ? null : t.format(HH_MM);
    }
}
