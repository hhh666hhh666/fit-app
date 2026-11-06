package com.example.fitapp.common;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 一张图片的内容 ＋ 字节数 ＋ MIME。原图用文件做内容、流式返回，不整张读进内存：
 * 单张最多 50MB，而线上堆只有 256MB（ADR 0051）。缩略图很小，直接用字节。
 */
public record PhotoData(Resource body, long length, String mime) {

    public static PhotoData of(byte[] data, String mime) {
        return new PhotoData(new ByteArrayResource(data), data.length, mime);
    }

    public static PhotoData of(Path file, String mime) {
        try {
            return new PhotoData(new FileSystemResource(file), Files.size(file), mime);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 直出图片（不走 Result 信封）；null 返回真实的 404，前端据此显示「图片已删除」之类的占位。 */
    public static ResponseEntity<Resource> response(PhotoData d) {
        if (d == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.mime()))
                .contentLength(d.length())
                // 客户端自己有 blob 缓存（ADR 0035），这里不再叠一层
                .cacheControl(CacheControl.noStore())
                .body(d.body());
    }
}
