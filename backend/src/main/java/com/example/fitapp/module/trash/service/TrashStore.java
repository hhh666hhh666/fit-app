package com.example.fitapp.module.trash.service;

import com.example.fitapp.common.BusinessException;
import com.example.fitapp.common.ResultCode;
import com.example.fitapp.module.trash.entity.TrashItem;
import com.example.fitapp.module.trash.mapper.TrashItemMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 写快照、读快照（ADR 0047）。业务模块删除时只依赖它，不依赖 {@link TrashService}：
 * 后者要拿到全部 {@link TrashHandler}，而处理器又要用业务服务，直接依赖会成环。
 */
@Component
@RequiredArgsConstructor
public class TrashStore {

    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    public static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("M月d日");
    public static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("M月d日 HH:mm");

    private static final int TITLE_MAX = 64;
    private static final int SUMMARY_MAX = 128;

    private final TrashItemMapper mapper;
    private final ObjectMapper objectMapper;

    public void put(Long userId, String type, Long sourceId, String title, String summary, long photoBytes,
                    Object snapshot) {
        mapper.insert(build(userId, type, sourceId, title, summary, photoBytes, json(snapshot)));
    }

    TrashItem build(Long userId, String type, Long sourceId, String title, String summary, long photoBytes,
                    String snapshot) {
        TrashItem item = new TrashItem();
        item.setUserId(userId);
        item.setType(type);
        item.setSourceId(sourceId);
        item.setTitle(clip(title, TITLE_MAX));
        item.setSummary(clip(summary, SUMMARY_MAX));
        item.setSnapshot(snapshot);
        item.setPhotoBytes(photoBytes);
        item.setDeletedAt(LocalDateTime.now(ZONE));
        return item;
    }

    /** 读快照。迁移后多出来的列取默认值，已删掉的列被忽略，所以旧快照也能恢复。 */
    public <T> T read(TrashItem item, Class<T> type) {
        try {
            return objectMapper.readValue(item.getSnapshot(), type);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "这一条的数据读不出来，没法恢复");
        }
    }

    String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "移到回收站失败，这次没有删除");
        }
    }

    private static String clip(String text, int max) {
        String s = text == null ? "" : text;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
