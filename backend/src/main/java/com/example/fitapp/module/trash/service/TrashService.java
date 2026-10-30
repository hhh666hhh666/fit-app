package com.example.fitapp.module.trash.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.fitapp.common.BusinessException;
import com.example.fitapp.common.ResultCode;
import com.example.fitapp.module.nutrition.service.DietService;
import com.example.fitapp.module.trash.dto.AbandonedSessionDTO;
import com.example.fitapp.module.trash.entity.TrashItem;
import com.example.fitapp.module.trash.mapper.TrashItemMapper;
import com.example.fitapp.module.trash.vo.TrashItemVO;
import com.example.fitapp.module.trash.vo.TrashRestoreVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 回收站（ADR 0047）：列表、恢复、彻底删除、到期清理，以及接收「开新的」丢掉的训练。 */
@Slf4j
@Service
public class TrashService {

    static final int KEEP_DAYS = 30;
    private static final int BATCH = 500;
    private static final int PAGE_MAX = 100;

    private final TrashItemMapper mapper;
    private final TrashStore store;
    private final ObjectMapper objectMapper;
    private final Map<String, TrashHandler> handlers;

    public TrashService(TrashItemMapper mapper, TrashStore store, ObjectMapper objectMapper,
                        List<TrashHandler> handlers) {
        this.mapper = mapper;
        this.store = store;
        this.objectMapper = objectMapper;
        this.handlers = handlers.stream().collect(Collectors.toMap(TrashHandler::type, Function.identity()));
    }

    /** 按删除时间倒序；types 为空＝全部类型。快照不带出来。 */
    public List<TrashItemVO> list(Long userId, List<String> types, Integer offset, Integer limit) {
        int size = (limit == null || limit < 1) ? 30 : Math.min(limit, PAGE_MAX);
        int start = (offset == null || offset < 0) ? 0 : offset;
        QueryWrapper<TrashItem> q = new QueryWrapper<TrashItem>()
                .select("id", "type", "title", "summary", "deleted_at")
                .eq("user_id", userId);
        if (types != null && !types.isEmpty()) {
            q.in("type", types);
        }
        q.orderByDesc("deleted_at", "id").last("LIMIT " + start + ", " + size);
        LocalDateTime now = LocalDateTime.now(TrashStore.ZONE);
        return mapper.selectList(q).stream().map(i -> toVO(i, now)).toList();
    }

    /** 恢复若干条；任一不归属则整批不动。丢掉的训练把快照交回前端，由它写回本机。 */
    @Transactional
    public List<TrashRestoreVO> restore(Long userId, List<Long> ids) {
        List<TrashItem> items = requireOwn(userId, ids);
        List<TrashRestoreVO> out = new ArrayList<>();
        for (TrashItem item : items) {
            TrashRestoreVO vo = new TrashRestoreVO();
            vo.setId(item.getId());
            vo.setType(item.getType());
            vo.setSourceId(item.getSourceId());
            vo.setNote(handler(item).restore(userId, item));
            if (TrashItem.SESSION.equals(item.getType())) {
                vo.setSession(store.read(item, JsonNode.class));
            }
            mapper.deleteById(item.getId());
            out.add(vo);
        }
        return out;
    }

    /** 彻底删除若干条；任一不归属则整批不动。 */
    public void purge(Long userId, List<Long> ids) {
        requireOwn(userId, ids).forEach(this::purgeOne);
    }

    /** 清空：一批一批删，单条失败只记日志，不挡后面的。 */
    public void clear(Long userId) {
        List<TrashItem> page;
        do {
            page = mapper.selectList(new QueryWrapper<TrashItem>().eq("user_id", userId).last("LIMIT " + BATCH));
            page.forEach(this::purgeOne);
        } while (page.size() == BATCH);
    }

    /** 清掉保留期已满的条目，返回清理条数。单条失败只记日志，第二天再试。 */
    public int purgeExpired() {
        LocalDateTime before = LocalDateTime.now(TrashStore.ZONE).minusDays(KEEP_DAYS);
        List<TrashItem> expired = mapper.selectList(new QueryWrapper<TrashItem>()
                .lt("deleted_at", before)
                .last("LIMIT " + BATCH));
        int cleaned = 0;
        for (TrashItem item : expired) {
            try {
                purgeOne(item);
                cleaned++;
            } catch (RuntimeException e) {
                log.warn("清理回收站条目失败：{}", item.getId(), e);
            }
        }
        return cleaned;
    }

    /** 列表里的缩略图；没有或不归属返回 null（由 Controller 转 404）。 */
    public DietService.PhotoData thumb(Long userId, Long id) {
        TrashItem item = mapper.selectOne(Wrappers.<TrashItem>query().eq("id", id).eq("user_id", userId));
        return item == null ? null : handler(item).thumb(item);
    }

    /**
     * 收下「开新的」丢掉的训练。App 断网时会先存本机、有网再补传，同一场可能传两次，
     * 所以按开始时刻去重：已有就直接算成功。
     */
    public void putAbandonedSession(Long userId, AbandonedSessionDTO dto) {
        JsonNode session = dto.getSession();
        long startedAt = session.path("startedAt").asLong(0);
        if (startedAt <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "训练数据不完整");
        }
        String dedupeKey = "session:" + startedAt;
        Long exists = mapper.selectCount(new QueryWrapper<TrashItem>()
                .eq("user_id", userId)
                .eq("dedupe_key", dedupeKey));
        if (exists != null && exists > 0) {
            return;
        }
        String title = session.path("title").asText("");
        TrashItem item = store.build(userId, TrashItem.SESSION, null, title.isBlank() ? "没结束的训练" : title,
                sessionSummary(session, startedAt), 0L, snapshotOf(dto));
        item.setDedupeKey(dedupeKey);
        try {
            mapper.insert(item);
        } catch (DuplicateKeyException e) {
            log.debug("丢掉的训练已经传过：{}", dedupeKey);
        }
    }

    private String snapshotOf(AbandonedSessionDTO dto) {
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "训练数据不完整");
        }
    }

    private static String sessionSummary(JsonNode session, long startedAt) {
        int done = 0;
        for (JsonNode block : session.path("blocks")) {
            for (JsonNode set : block.path("sets")) {
                if (set.path("done").asBoolean(false)) {
                    done++;
                }
            }
        }
        LocalDateTime started = LocalDateTime.ofInstant(Instant.ofEpochMilli(startedAt), TrashStore.ZONE);
        return TrashStore.DAY_TIME.format(started) + " 开始 · 已完成 " + done + " 组";
    }

    private void purgeOne(TrashItem item) {
        // 先删行、后删文件（ADR 0026）：最坏只留一个无害的孤儿文件。
        if (mapper.deleteById(item.getId()) > 0) {
            handler(item).purge(item);
        }
    }

    private List<TrashItem> requireOwn(Long userId, List<Long> ids) {
        List<Long> distinct = ids.stream().distinct().toList();
        List<TrashItem> items = mapper.selectList(new QueryWrapper<TrashItem>()
                .eq("user_id", userId)
                .in("id", distinct));
        if (items.size() != distinct.size()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "回收站里没有这一条，可能已经恢复或清理了");
        }
        return items;
    }

    private TrashHandler handler(TrashItem item) {
        TrashHandler h = handlers.get(item.getType());
        if (h == null) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "不认识的回收站类型：" + item.getType());
        }
        return h;
    }

    private static TrashItemVO toVO(TrashItem i, LocalDateTime now) {
        TrashItemVO vo = new TrashItemVO();
        vo.setId(i.getId());
        vo.setType(i.getType());
        vo.setTitle(i.getTitle());
        vo.setSummary(i.getSummary());
        vo.setDeletedAt(i.getDeletedAt());
        // 到期那天清理任务跑之前也还在：最少显示 1 天
        vo.setRemainingDays(Math.max(1, KEEP_DAYS - ChronoUnit.DAYS.between(i.getDeletedAt(), now)));
        vo.setHasThumb(TrashItem.MEAL_PHOTO.equals(i.getType()) || TrashItem.DIARY_PHOTO.equals(i.getType()));
        return vo;
    }
}
