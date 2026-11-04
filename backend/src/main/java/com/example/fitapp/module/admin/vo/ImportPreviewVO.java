package com.example.fitapp.module.admin.vo;

import java.util.List;

/**
 * 预置食物导入的预检结果（也是正式导入的回执）：新增几条、更新几条、哪些无效被跳过。
 * index 是这一条在 JSON 数组里的位置（从 1 开始）。
 */
public record ImportPreviewVO(int create, int update, List<Invalid> invalid) {

    public record Invalid(int index, String name, String reason) {
    }
}
