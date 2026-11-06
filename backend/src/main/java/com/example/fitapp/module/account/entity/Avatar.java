package com.example.fitapp.module.account.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 一张头像（ADR 0051）。表结构以 {@code V38__avatar.sql} 为准。 */
@Data
@TableName("avatar")
public class Avatar {

    /** 自增；从回收站恢复时带着原 id 插入，MyBatis-Plus 会把它写进去（ADR 0047）。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String path;
    private String mime;
    private Integer width;
    private Integer height;
    private Integer sizeBytes;
    private Integer extraBytes;
    private Integer cropX;
    private Integer cropY;
    private Integer cropSize;
    private LocalDateTime usedAt;
    private LocalDateTime createdAt;
}
