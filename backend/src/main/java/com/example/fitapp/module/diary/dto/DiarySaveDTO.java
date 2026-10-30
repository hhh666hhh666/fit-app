package com.example.fitapp.module.diary.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/** 写（或改）一篇日记：标题和正文至少有一项（服务端校验）；时刻缺省为现在。 */
@Data
public class DiarySaveDTO {

    private LocalDateTime writtenAt;

    @Size(max = 50, message = "标题最多 50 字")
    private String title;

    @Size(max = 20000, message = "正文最多 2 万字")
    private String content;
}
