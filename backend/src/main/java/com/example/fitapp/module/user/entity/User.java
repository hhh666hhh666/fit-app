package com.example.fitapp.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户。表结构以迁移脚本 {@code V2__user.sql} 为准。
 */
@Data
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** 昵称：显示名，可空、可重名，不能拿来登录（ADR 0050）。 */
    private String nickname;

    private String passwordHash;

    /** 令牌版本：改密码时加一，签发时写进令牌，校验时对不上就拒绝（ADR 0050）。 */
    private Integer tokenVersion;

    /** 管理员标记：能进管理端（ADR 0048）。界面上没有授予入口，只能手写 SQL。 */
    private Boolean admin;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
