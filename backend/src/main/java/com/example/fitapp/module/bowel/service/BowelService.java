package com.example.fitapp.module.bowel.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.fitapp.common.BusinessException;
import com.example.fitapp.common.ResultCode;
import com.example.fitapp.module.bowel.dto.BowelSaveDTO;
import com.example.fitapp.module.bowel.entity.BowelRecord;
import com.example.fitapp.module.bowel.mapper.BowelRecordMapper;
import com.example.fitapp.module.bowel.vo.BowelStatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 排便记录（ADR 0061）：记一次、改、删（进回收站）、历史分页、统计。
 */
@Service
@RequiredArgsConstructor
public class BowelService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    /** 统计最多看多少天（「全部」也按这个封顶，防呆）。 */
    static final int MAX_STATS_DAYS = 3660;

    private final BowelRecordMapper mapper;
    private final BowelTrash trash;

    /** 历史：最近的在前，触底分页。 */
    public List<BowelRecord> list(Long userId, int limit, int offset) {
        return mapper.selectList(Wrappers.<BowelRecord>lambdaQuery()
                .eq(BowelRecord::getUserId, userId)
                .orderByDesc(BowelRecord::getOccurredAt)
                .orderByDesc(BowelRecord::getId)
                .last("LIMIT " + Math.max(offset, 0) + ", " + Math.min(Math.max(limit, 1), 100)));
    }

    /** 记一次：kind 必填，时刻缺省为现在（只到分）。 */
    public BowelRecord create(Long userId, BowelSaveDTO dto) {
        if (dto.getKind() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "要说明是大便还是小便");
        }
        BowelRecord r = new BowelRecord();
        r.setUserId(userId);
        apply(r, dto);
        if (r.getOccurredAt() == null) {
            r.setOccurredAt(LocalDateTime.now(ZONE).truncatedTo(ChronoUnit.MINUTES));
        }
        mapper.insert(r);
        return r;
    }

    /** 改一次：时刻、形态、备注整体覆盖（kind 不给就不改）。 */
    public BowelRecord update(Long userId, Long id, BowelSaveDTO dto) {
        BowelRecord r = requireOwn(userId, id);
        LocalDateTime keepTime = r.getOccurredAt();
        apply(r, dto);
        if (r.getOccurredAt() == null) {
            r.setOccurredAt(keepTime);
        }
        // 显式 set：全局 not_null 更新策略会跳过 null，清掉形态、备注要走 wrapper
        mapper.update(null, Wrappers.<BowelRecord>lambdaUpdate()
                .eq(BowelRecord::getId, id)
                .eq(BowelRecord::getUserId, userId)
                .set(BowelRecord::getKind, r.getKind())
                .set(BowelRecord::getOccurredAt, r.getOccurredAt())
                .set(BowelRecord::getBristol, r.getBristol())
                .set(BowelRecord::getColor, r.getColor())
                .set(BowelRecord::getNote, r.getNote()));
        return r;
    }

    /** 删一次：先进回收站（ADR 0047）。 */
    public void delete(Long userId, Long id) {
        trash.put(userId, requireOwn(userId, id));
        mapper.deleteById(id);
    }

    private void apply(BowelRecord r, BowelSaveDTO dto) {
        if (dto.getKind() != null) {
            r.setKind(dto.getKind());
        }
        r.setOccurredAt(dto.getOccurredAt() == null ? null : dto.getOccurredAt().truncatedTo(ChronoUnit.MINUTES));
        // 形态只对大便有意义：小便给了也清掉
        r.setBristol(BowelRecord.STOOL.equals(r.getKind()) ? dto.getBristol() : null);
        r.setColor(BowelRecord.URINE.equals(r.getKind()) ? dto.getColor() : null);
        String note = dto.getNote() == null ? null : dto.getNote().strip();
        r.setNote(note == null || note.isEmpty() ? null : note);
    }

    private BowelRecord requireOwn(Long userId, Long id) {
        BowelRecord r = mapper.selectById(id);
        if (r == null || !userId.equals(r.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "记录不存在");
        }
        return r;
    }

    /**
     * 统计：{@code days} 天（含今天）；{@code days} 为 0 或负数表示从第一条记录起。
     * 平均间隔按相邻两次的时间差算（范围内的记录之间）。
     */
    public BowelStatsVO stats(Long userId, int days) {
        LocalDate to = LocalDate.now(ZONE);
        LocalDate from;
        if (days > 0) {
            from = to.minusDays(Math.min(days, MAX_STATS_DAYS) - 1L);
        } else {
            BowelRecord first = mapper.selectOne(Wrappers.<BowelRecord>lambdaQuery()
                    .eq(BowelRecord::getUserId, userId)
                    .orderByAsc(BowelRecord::getOccurredAt)
                    .last("LIMIT 1"));
            from = first == null ? to : first.getOccurredAt().toLocalDate();
            if (ChronoUnit.DAYS.between(from, to) >= MAX_STATS_DAYS) {
                from = to.minusDays(MAX_STATS_DAYS - 1L);
            }
        }
        List<BowelRecord> rows = mapper.selectList(Wrappers.<BowelRecord>lambdaQuery()
                .eq(BowelRecord::getUserId, userId)
                .ge(BowelRecord::getOccurredAt, from.atStartOfDay())
                .lt(BowelRecord::getOccurredAt, to.plusDays(1).atStartOfDay())
                .orderByAsc(BowelRecord::getOccurredAt)
                .orderByAsc(BowelRecord::getId));
        return compute(rows, from, to);
    }

    static BowelStatsVO compute(List<BowelRecord> rows, LocalDate from, LocalDate to) {
        BowelStatsVO vo = new BowelStatsVO();
        vo.setFrom(from);
        vo.setTo(to);
        Map<LocalDate, BowelStatsVO.Day> byDate = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            BowelStatsVO.Day day = new BowelStatsVO.Day();
            day.setDate(d);
            byDate.put(d, day);
        }
        LocalDateTime lastStool = null;
        LocalDateTime lastUrine = null;
        long stoolGapMinutes = 0;
        long urineGapMinutes = 0;
        int stoolGaps = 0;
        int urineGaps = 0;
        for (BowelRecord r : rows) {
            BowelStatsVO.Day day = byDate.get(r.getOccurredAt().toLocalDate());
            boolean stool = BowelRecord.STOOL.equals(r.getKind());
            BowelStatsVO.Kind k = stool ? vo.getStool() : vo.getUrine();
            k.setCount(k.getCount() + 1);
            k.getHours()[r.getOccurredAt().getHour()]++;
            if (stool) {
                if (day != null) day.setStool(day.getStool() + 1);
                if (r.getBristol() != null && r.getBristol() >= 1 && r.getBristol() <= 7) {
                    vo.getBristol()[r.getBristol() - 1]++;
                }
                if (lastStool != null) {
                    stoolGapMinutes += Duration.between(lastStool, r.getOccurredAt()).toMinutes();
                    stoolGaps++;
                }
                lastStool = r.getOccurredAt();
            } else {
                if (day != null) day.setUrine(day.getUrine() + 1);
                if (r.getColor() != null && r.getColor() >= 1 && r.getColor() <= 7) {
                    vo.getColor()[r.getColor() - 1]++;
                }
                if (lastUrine != null) {
                    urineGapMinutes += Duration.between(lastUrine, r.getOccurredAt()).toMinutes();
                    urineGaps++;
                }
                lastUrine = r.getOccurredAt();
            }
        }
        long span = ChronoUnit.DAYS.between(from, to) + 1;
        finish(vo.getStool(), span, stoolGapMinutes, stoolGaps);
        finish(vo.getUrine(), span, urineGapMinutes, urineGaps);
        vo.getDays().addAll(byDate.values());
        return vo;
    }

    private static void finish(BowelStatsVO.Kind k, long span, long gapMinutes, int gaps) {
        k.setPerDay(Math.round(k.getCount() * 10.0 / span) / 10.0);
        k.setAvgIntervalHours(gaps == 0 ? null : Math.round(gapMinutes / 6.0 / gaps) / 10.0);
    }
}
