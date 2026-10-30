package com.example.fitapp.module.diary.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

/** 每天清理没有任何日记引用满 30 天的图（ADR 0044）。排在每日备份（03:30、03:35）之后。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiaryPhotoCleaner {

    private static final int KEEP_DAYS = 30;

    private final DiaryPhotoService photoService;

    @Scheduled(cron = "0 10 4 * * *", zone = "Asia/Shanghai")
    public void clean() {
        int cleaned = photoService.cleanupUnreferenced(
                LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusDays(KEEP_DAYS));
        if (cleaned > 0) {
            log.info("清理了 {} 张没有日记引用的图片", cleaned);
        }
    }
}
