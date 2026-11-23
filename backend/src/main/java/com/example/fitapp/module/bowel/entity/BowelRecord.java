package com.example.fitapp.module.bowel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 一次大便或小便（ADR 0061）。表结构以 V47 为准。 */
@Data
@TableName("bowel_record")
public class BowelRecord {

    public static final String STOOL = "stool";
    public static final String URINE = "urine";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** stool＝大便，urine＝小便。 */
    private String kind;

    /** 时刻（只到分）。 */
    private LocalDateTime occurredAt;

    /** 布里斯托形态 1–7：只有大便可以填，选填。 */
    private Integer bristol;

    /** 小便颜色 1–7：无色透明、浅黄、黄、深黄、琥珀、茶褐、红／粉；只有小便可以填，选填（ADR 0061 修订）。 */
    private Integer color;

    private String note;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
