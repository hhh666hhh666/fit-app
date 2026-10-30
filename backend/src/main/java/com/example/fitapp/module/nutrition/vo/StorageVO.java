package com.example.fitapp.module.nutrition.vo;

import lombok.Data;

/**
 * 存储占用（ADR 0013）。按**分类**设计，目前只有「图片」一类——
 * 将来要加别的占用（如训练数据）只需在 VO 里加字段，不必改接口形态。
 */
@Data
public class StorageVO {

    /** 图片已用（字节，原图 ＋ 缩略图；含餐照和日记图片）。 */
    private Long photoBytes;

    /** 照片张数（相册入口与「我的」页展示用；含餐照和日记图片）。 */
    private Integer photoCount;

    /** 图片上限（字节，来自配置 `fitapp.storage.photo-quota-bytes`）。 */
    private Long photoQuotaBytes;
}
