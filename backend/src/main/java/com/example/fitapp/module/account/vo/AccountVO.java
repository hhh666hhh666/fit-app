package com.example.fitapp.module.account.vo;

import lombok.Data;

/** 账号页的数据（ADR 0050）：用户名、昵称（没设为 null）。 */
@Data
public class AccountVO {
    private Long userId;
    private String username;
    private String nickname;
}
