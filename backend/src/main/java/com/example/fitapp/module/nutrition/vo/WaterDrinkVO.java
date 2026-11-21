package com.example.fitapp.module.nutrition.vo;

import lombok.Data;

/** 一笔饮水（ADR 0059）。 */
@Data
public class WaterDrinkVO {

    private Long id;

    private Integer ml;

    /** 时刻 HH:mm；旧的每日累计迁来的为 null。 */
    private String time;
}
