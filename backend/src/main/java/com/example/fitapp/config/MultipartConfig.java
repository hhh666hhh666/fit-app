package com.example.fitapp.config;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/**
 * multipart 的限制由图片上限 {@code fitapp.storage.photo-max-mb} 推出，上限只有这一处配置（ADR 0051）。
 *
 * <p>容器这一层故意放宽 1～2MB：刚超上限的图应当由 PhotoUploadPolicy 给出「照片超过 50MB」，
 * 而不是在容器层被拦成笼统的错误。文件一律先落临时目录（阈值 0），不在内存里攒。
 */
@Configuration
public class MultipartConfig {

    @Bean
    public MultipartConfigElement multipartConfigElement(@Value("${fitapp.storage.photo-max-mb:50}") int maxMb) {
        MultipartConfigFactory f = new MultipartConfigFactory();
        f.setMaxFileSize(DataSize.ofMegabytes(maxMb + 1L));
        f.setMaxRequestSize(DataSize.ofMegabytes(maxMb + 2L));
        f.setFileSizeThreshold(DataSize.ofBytes(0));
        return f.createMultipartConfig();
    }
}
