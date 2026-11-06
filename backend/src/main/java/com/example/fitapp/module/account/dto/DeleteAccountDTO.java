package com.example.fitapp.module.account.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 注销账号的两道确认：密码 ＋ 手打一遍用户名（ADR 0050）。 */
@Data
public class DeleteAccountDTO {

    @NotBlank(message = "请输入密码")
    private String password;

    @NotBlank(message = "请输入用户名")
    private String username;
}
