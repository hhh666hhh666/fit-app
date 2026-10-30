package com.example.fitapp.module.body.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.fitapp.common.BusinessException;
import com.example.fitapp.common.ResultCode;
import com.example.fitapp.module.body.dto.BodySaveDTO;
import com.example.fitapp.module.body.entity.BodyRecord;
import com.example.fitapp.module.body.mapper.BodyRecordMapper;
import com.example.fitapp.module.body.vo.BodyOverviewVO;
import com.example.fitapp.module.body.vo.BodyRecordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 身体档案：录入（体重／四围记哪项填哪项）、一屏总览、记录明细（含相邻差）、修改、删除。
 * 自用数据量小，总览一次拉全量在内存组装。
 *
 * <p>两个口径（ADR 0017）：
 * <ul>
 *   <li>一条记录＝一次测定，体重与四围同一行（不拆表）；某项没量就是 null。</li>
 *   <li>图表序列**按日聚合、同一天取最小**；列表不聚合，逐条显示到分。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class BodyService {

    private final BodyRecordMapper bodyRecordMapper;
    private final BodyTrash bodyTrash;

    @Transactional
    public BodyRecordVO create(Long userId, BodySaveDTO dto) {
        requireAny(dto);

        BodyRecord r = new BodyRecord();
        r.setUserId(userId);
        r.setMeasuredAt(dto.getMeasuredAt() == null ? LocalDateTime.now() : dto.getMeasuredAt());
        applyValues(r, dto);
        bodyRecordMapper.insert(r);
        return toRow(r, null);
    }

    /** 修改一条测定（身体档案页点行进编辑弹层）：时刻与五项数值整体覆盖。 */
    @Transactional
    public BodyRecordVO update(Long userId, Long id, BodySaveDTO dto) {
        requireAny(dto);
        BodyRecord r = requireOwn(userId, id);
        if (dto.getMeasuredAt() != null) {
            r.setMeasuredAt(dto.getMeasuredAt());
        }
        applyValues(r, dto);
        bodyRecordMapper.update(null, Wrappers.<BodyRecord>update()
                .eq("id", id)
                .eq("user_id", userId)
                .set("measured_at", r.getMeasuredAt())
                .set("weight", r.getWeight())
                .set("body_fat", r.getBodyFat())
                .set("chest", r.getChest())
                .set("waist", r.getWaist())
                .set("hip", r.getHip())
                .set("arm", r.getArm())
                .set("note", r.getNote()));
        return toRow(r, null);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        bodyTrash.put(userId, requireOwn(userId, id));
        bodyRecordMapper.deleteById(id);
    }

    /**
     * 批量删除（身体档案页长按多选删除）：先整体校验归属，再逐条删——
     * 任一不归属则整批回滚，不留「删了一半」的中间态。ids 去重后再删。
     */
    @Transactional
    public void batchDelete(Long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<BodyRecord> distinct = ids.stream().distinct().map(id -> requireOwn(userId, id)).toList();
        for (BodyRecord r : distinct) {
            bodyTrash.put(userId, r);
            bodyRecordMapper.deleteById(r.getId());
        }
    }

    public BodyOverviewVO overview(Long userId) {
        List<BodyRecord> all = listAsc(userId);

        List<BodyRecord> weights = all.stream().filter(r -> r.getWeight() != null).toList();

        BodyOverviewVO vo = new BodyOverviewVO();
        vo.setIsLowest(false);
        vo.setWeightSeries(new ArrayList<>());
        vo.setGirthSeries(new ArrayList<>());
        vo.setBodyFatSeries(new ArrayList<>());

        if (!weights.isEmpty()) {
            BodyRecord latest = weights.get(weights.size() - 1);
            BodyOverviewVO.Latest l = new BodyOverviewVO.Latest();
            l.setDate(latest.getMeasuredAt());
            l.setWeight(latest.getWeight());
            l.setNote(latest.getNote());
            vo.setLatest(l);

            // 体重序列：按日聚合取最小（ADR 0017）
            for (Map.Entry<LocalDate, BigDecimal> e : minByDay(weights, BodyRecord::getWeight).entrySet()) {
                vo.getWeightSeries().add(new BodyOverviewVO.WeightPoint(e.getKey(), e.getValue()));
            }

            // 「阶段最低」：当前体重就是序列最小值（并列算），且序列 ≥2 条才有「阶段」意义
            BigDecimal min = weights.stream()
                    .map(BodyRecord::getWeight)
                    .min(Comparator.naturalOrder())
                    .orElse(null);
            vo.setIsLowest(weights.size() >= 2 && latest.getWeight().compareTo(min) <= 0);

            // 较上周：与「最新记录时刻 −7 天及更早」的最近一条体重之差
            LocalDateTime cutoff = latest.getMeasuredAt().minusDays(7);
            BodyRecord lastWeek = null;
            for (int i = weights.size() - 1; i >= 0; i--) {
                BodyRecord r = weights.get(i);
                if (!r.getMeasuredAt().isAfter(cutoff)) {
                    lastWeek = r;
                    break;
                }
            }
            if (lastWeek != null) {
                vo.setWeekDiff(latest.getWeight().subtract(lastWeek.getWeight()));
            }
        }

        // 围度序列：按日聚合，四个部位各自取当天最小
        List<BodyRecord> girths = all.stream()
                .filter(r -> r.getChest() != null || r.getWaist() != null
                        || r.getHip() != null || r.getArm() != null)
                .toList();
        Map<LocalDate, BodyOverviewVO.GirthPoint> byDay = new LinkedHashMap<>();
        for (BodyRecord r : girths) {
            BodyOverviewVO.GirthPoint p = byDay.computeIfAbsent(
                    r.getMeasuredAt().toLocalDate(), k -> new BodyOverviewVO.GirthPoint());
            p.setChest(minOf(p.getChest(), r.getChest()));
            p.setWaist(minOf(p.getWaist(), r.getWaist()));
            p.setHip(minOf(p.getHip(), r.getHip()));
            p.setArm(minOf(p.getArm(), r.getArm()));
        }
        byDay.forEach((d, p) -> {
            p.setDate(d);
            vo.getGirthSeries().add(p);
        });

        // 最新围度：四围任一非空的最近一条
        girths.stream()
                .max(Comparator.comparing(BodyRecord::getMeasuredAt).thenComparing(BodyRecord::getId))
                .ifPresent(r -> {
                    BodyOverviewVO.Girth g = new BodyOverviewVO.Girth();
                    g.setDate(r.getMeasuredAt());
                    g.setChest(r.getChest());
                    g.setWaist(r.getWaist());
                    g.setHip(r.getHip());
                    g.setArm(r.getArm());
                    vo.setGirth(g);
                });

        // 体脂率：与体重同一套聚合口径（按日取最小）；只记录、不参与任何计算（ADR 0030）
        List<BodyRecord> fats = all.stream().filter(r -> r.getBodyFat() != null).toList();
        for (Map.Entry<LocalDate, BigDecimal> e : minByDay(fats, BodyRecord::getBodyFat).entrySet()) {
            vo.getBodyFatSeries().add(new BodyOverviewVO.BodyFatPoint(e.getKey(), e.getValue()));
        }
        fats.stream()
                .max(Comparator.comparing(BodyRecord::getMeasuredAt).thenComparing(BodyRecord::getId))
                .ifPresent(r -> {
                    BodyOverviewVO.BodyFat f = new BodyOverviewVO.BodyFat();
                    f.setDate(r.getMeasuredAt());
                    f.setValue(r.getBodyFat());
                    vo.setBodyFat(f);
                });

        return vo;
    }

    /**
     * 记录明细（体重与围度同一条时间线，**不再只查体重**——只记围度的那条也能看见、能删能改）。
     * diff＝与时间上相邻「上一条有体重的记录」之差——正序计算、倒序输出（最新在前）。
     *
     * @param offset 跳过最新的 N 条（前端触底分页）
     */
    public List<BodyRecordVO> records(Long userId, int limit, int offset) {
        List<BodyRecord> all = listAsc(userId);

        List<BodyRecordVO> asc = new ArrayList<>();
        BigDecimal prev = null;
        for (BodyRecord r : all) {
            BodyRecordVO vo = toRow(r, r.getWeight() == null || prev == null ? null : r.getWeight().subtract(prev));
            asc.add(vo);
            if (r.getWeight() != null) {
                prev = r.getWeight();
            }
        }

        List<BodyRecordVO> out = new ArrayList<>();
        int skipped = 0;
        for (int i = asc.size() - 1; i >= 0 && out.size() < limit; i--) {
            if (skipped < offset) {
                skipped++;
                continue;
            }
            out.add(asc.get(i));
        }
        return out;
    }

    // ── 内部 ──

    private List<BodyRecord> listAsc(Long userId) {
        return bodyRecordMapper.selectList(
                Wrappers.<BodyRecord>lambdaQuery()
                        .eq(BodyRecord::getUserId, userId)
                        .orderByAsc(BodyRecord::getMeasuredAt)
                        .orderByAsc(BodyRecord::getId));
    }

    /** 按测定日分组取最小值；输入须已按（时刻, id）升序——同日多条时保留最先出现的那天顺序。 */
    private Map<LocalDate, BigDecimal> minByDay(List<BodyRecord> rows, Function<BodyRecord, BigDecimal> get) {
        Map<LocalDate, BigDecimal> out = new LinkedHashMap<>();
        for (BodyRecord r : rows) {
            LocalDate d = r.getMeasuredAt().toLocalDate();
            BigDecimal v = get.apply(r);
            BigDecimal cur = out.get(d);
            out.put(d, cur == null ? v : cur.min(v));
        }
        return out;
    }

    private BigDecimal minOf(BigDecimal a, BigDecimal b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.min(b);
    }

    private void applyValues(BodyRecord r, BodySaveDTO dto) {
        r.setWeight(dto.getWeight());
        r.setBodyFat(dto.getBodyFat());
        r.setChest(dto.getChest());
        r.setWaist(dto.getWaist());
        r.setHip(dto.getHip());
        r.setArm(dto.getArm());
        // note 列是 NOT NULL：不传就记空串（编辑页总是带这一项，清空即空串）
        r.setNote(dto.getNote() == null ? "" : dto.getNote().trim());
    }

    /** 体重、体脂、四围至少一项非空（前端也会拦一次，这里是服务端闸）。 */
    private void requireAny(BodySaveDTO dto) {
        boolean hasAny = dto.getWeight() != null || dto.getBodyFat() != null || dto.getChest() != null
                || dto.getWaist() != null || dto.getHip() != null || dto.getArm() != null;
        if (!hasAny) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "体重、体脂和围度至少填一项");
        }
    }

    private BodyRecord requireOwn(Long userId, Long id) {
        BodyRecord r = bodyRecordMapper.selectById(id);
        if (r == null || !userId.equals(r.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "记录不存在");
        }
        return r;
    }

    private BodyRecordVO toRow(BodyRecord r, BigDecimal diff) {
        BodyRecordVO vo = new BodyRecordVO();
        vo.setId(r.getId());
        vo.setDate(r.getMeasuredAt());
        vo.setWeight(r.getWeight());
        vo.setBodyFat(r.getBodyFat());
        vo.setChest(r.getChest());
        vo.setWaist(r.getWaist());
        vo.setHip(r.getHip());
        vo.setArm(r.getArm());
        vo.setNote(r.getNote());
        vo.setDiff(diff);
        return vo;
    }
}
