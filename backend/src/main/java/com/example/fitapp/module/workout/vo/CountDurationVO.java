package com.example.fitapp.module.workout.vo;

import lombok.Data;

/**
 * 周期内训练次数与总时长（统计页双格卡）。
 */
@Data
public class CountDurationVO {

    private Integer cnt;

    /** 秒（SUM(duration_sec)，空表为 0）。 */
    private Long dur;
}
