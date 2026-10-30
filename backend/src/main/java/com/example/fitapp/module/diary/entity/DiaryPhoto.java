package com.example.fitapp.module.diary.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 日记图片：实体在文件系统，这里只留路径与元数据（ADR 0026、ADR 0044）。 */
@Data
@TableName("diary_photo")
public class DiaryPhoto {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String mime;

    private Integer sizeBytes;

    private Integer thumbBytes;

    /** 相对路径（不含扩展名）：{userId}/diary/{随机名}。 */
    private String path;

    /** 从何时起没有任何日记引用；为空＝正被引用。 */
    private LocalDateTime unreferencedSince;

    private LocalDateTime createdAt;
}
