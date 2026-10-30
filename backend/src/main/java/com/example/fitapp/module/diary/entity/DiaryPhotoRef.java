package com.example.fitapp.module.diary.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 哪篇日记引用了哪张图；保存时按正文同步。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("diary_photo_ref")
public class DiaryPhotoRef {

    private Long entryId;

    private Long photoId;
}
