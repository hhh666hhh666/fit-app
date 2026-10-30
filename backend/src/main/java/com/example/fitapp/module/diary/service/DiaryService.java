package com.example.fitapp.module.diary.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.fitapp.common.BusinessException;
import com.example.fitapp.common.ResultCode;
import com.example.fitapp.module.diary.dto.DiarySaveDTO;
import com.example.fitapp.module.diary.entity.DiaryEntry;
import com.example.fitapp.module.diary.mapper.DiaryEntryMapper;
import com.example.fitapp.module.diary.vo.DiaryEntryVO;
import com.example.fitapp.module.diary.vo.DiaryListItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 日记（ADR 0044）：一篇＝时刻 ＋ 可选标题 ＋ Markdown 正文，一天可写多篇；
 * 列表按月翻看，有关键词时搜全部日记。
 */
@Service
@RequiredArgsConstructor
public class DiaryService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    /** 手机时钟可能比服务器快一点，不能因此拦下一篇刚写完的日记。 */
    private static final int FUTURE_TOLERANCE_MINUTES = 5;
    private static final int PREVIEW_CHARS = 200;

    private final DiaryEntryMapper entryMapper;
    private final DiaryPhotoService photoService;
    private final DiaryTrash diaryTrash;

    public List<DiaryListItemVO> list(Long userId, String month, String keyword, int limit, int offset) {
        QueryWrapper<DiaryEntry> q = new QueryWrapper<DiaryEntry>()
                .select("id", "written_at", "title", "LEFT(content, " + PREVIEW_CHARS + ") AS content")
                .eq("user_id", userId);
        if (keyword != null && !keyword.isBlank()) {
            String like = escapeLike(keyword.trim());
            q.and(w -> w.like("title", like).or().like("content", like));
        } else {
            YearMonth m = parseMonth(month);
            q.ge("written_at", m.atDay(1).atStartOfDay())
                    .lt("written_at", m.plusMonths(1).atDay(1).atStartOfDay());
        }
        q.orderByDesc("written_at", "id").last("LIMIT " + Math.max(offset, 0) + ", " + Math.max(limit, 1));
        return entryMapper.selectList(q).stream().map(e -> {
            DiaryListItemVO vo = new DiaryListItemVO();
            vo.setId(e.getId());
            vo.setWrittenAt(e.getWrittenAt());
            vo.setTitle(e.getTitle());
            vo.setPreview(e.getContent());
            return vo;
        }).toList();
    }

    public DiaryEntryVO detail(Long userId, Long id) {
        return toVO(requireOwn(userId, id));
    }

    @Transactional
    public DiaryEntryVO create(Long userId, DiarySaveDTO dto) {
        DiaryEntry e = new DiaryEntry();
        e.setUserId(userId);
        apply(e, dto);
        entryMapper.insert(e);
        photoService.syncRefs(userId, e.getId(), e.getContent());
        return toVO(e);
    }

    /** 整篇覆盖；多端同改时后保存的覆盖先保存的（ADR 0044）。 */
    @Transactional
    public DiaryEntryVO update(Long userId, Long id, DiarySaveDTO dto) {
        DiaryEntry e = requireOwn(userId, id);
        apply(e, dto);
        entryMapper.updateById(e);
        photoService.syncRefs(userId, id, e.getContent());
        return toVO(e);
    }

    /** 批量删除：先整体校验归属，任一不归属则整批不动。图默认留在相册，见 ADR 0046。 */
    @Transactional
    public void batchDelete(Long userId, List<Long> ids, boolean withPhotos) {
        List<DiaryEntry> distinct = ids.stream().distinct().map(id -> requireOwn(userId, id)).toList();
        for (DiaryEntry e : distinct) {
            diaryTrash.put(userId, e, photoService.releaseEntry(e.getId(), withPhotos));
            entryMapper.deleteById(e.getId());
        }
    }

    private void apply(DiaryEntry e, DiarySaveDTO dto) {
        String title = dto.getTitle() == null ? "" : dto.getTitle().trim();
        String content = dto.getContent() == null ? "" : dto.getContent();
        if (title.isEmpty() && content.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "写点什么再保存");
        }
        LocalDateTime now = LocalDateTime.now(ZONE);
        LocalDateTime writtenAt = dto.getWrittenAt() == null
                ? (e.getWrittenAt() == null ? now : e.getWrittenAt())
                : dto.getWrittenAt();
        if (writtenAt.isAfter(now.plusMinutes(FUTURE_TOLERANCE_MINUTES))) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "时刻不能选未来");
        }
        e.setWrittenAt(writtenAt.withSecond(0).withNano(0));
        e.setTitle(title);
        e.setContent(content);
    }

    private DiaryEntry requireOwn(Long userId, Long id) {
        DiaryEntry e = entryMapper.selectOne(Wrappers.<DiaryEntry>lambdaQuery()
                .eq(DiaryEntry::getId, id)
                .eq(DiaryEntry::getUserId, userId));
        if (e == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "这篇日记不存在");
        }
        return e;
    }

    private static DiaryEntryVO toVO(DiaryEntry e) {
        DiaryEntryVO vo = new DiaryEntryVO();
        vo.setId(e.getId());
        vo.setWrittenAt(e.getWrittenAt());
        vo.setTitle(e.getTitle());
        vo.setContent(e.getContent());
        vo.setUpdatedAt(e.getUpdatedAt());
        return vo;
    }

    /** 主人搜 100% 时要找的是字面上的百分号，不是通配符。 */
    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now(ZONE);
        }
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "月份参数应为 YYYY-MM");
        }
    }
}
