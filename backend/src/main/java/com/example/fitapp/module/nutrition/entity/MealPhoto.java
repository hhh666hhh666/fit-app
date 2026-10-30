package com.example.fitapp.module.nutrition.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 餐照（一餐可以有多张，ADR 0045）。
 *
 * <p>图片实体**存在文件系统**（见 {@link com.example.fitapp.module.nutrition.storage.PhotoStorage}，
 * ADR 0026），本行只留元数据与相对路径。
 */
@Data
@TableName("meal_photo")
public class MealPhoto {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private LocalDate logDate;

    private String meal;

    private String mime;

    private Integer sizeBytes;

    /** 图片相对路径（不含扩展名与根目录）：{userId}/{logDate}/{meal}，新照片再带随机串。见 ADR 0026、0045。 */
    private String path;

    /** 缩略图字节数（缩略图文件在文件系统里）；老数据无缩略图时为 NULL。 */
    private Integer thumbBytes;

    /**
     * 图片数据。⚠️ **仅供历史遗留搬运使用**：由
     * {@link com.example.fitapp.module.nutrition.storage.MealPhotoFileMigrator} 落盘后置 NULL。
     * 新数据一律走文件系统（{@code path}），读取端不再碰它。
     */
    private byte[] image;

    /** 缩略图数据。⚠️ 同 {@code image}：搬运完成后为 NULL，读取端回退用原图。 */
    private byte[] thumb;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
