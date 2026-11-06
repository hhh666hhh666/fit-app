package com.example.fitapp.module.account.dto;

import lombok.Data;

/** 改昵称：空或全是空白＝清掉昵称；长度在服务层按字符数校验（emoji 也算一个）。 */
@Data
public class NicknameDTO {
    private String nickname;
}
