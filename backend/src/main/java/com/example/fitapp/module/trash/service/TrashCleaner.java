package com.example.fitapp.module.trash.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每天清理保留期已满的回收站条目（ADR 0047）。排在每日备份（03:30、03:35）之后。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrashCleaner {

    private final TrashService trashService;

    @Scheduled(cron = "0 10 4 * * *", zone = "Asia/Shanghai")
    public void clean() {
        int cleaned = trashService.purgeExpired();
        if (cleaned > 0) {
            log.info("清理了 {} 条到期的回收站条目", cleaned);
        }
    }
}
