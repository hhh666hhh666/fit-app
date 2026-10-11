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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 身体档案：录入（体重／四围记哪项填哪项）、一屏总览、体重明细（含相邻差）。
 * 自用数据量小，总览一次拉全量在内存组装。
 */
@Service
@RequiredArgsConstructor
public class BodyService {

    private final BodyRecordMapper bodyRecordMapper;

    @Transactional
    public BodyRecordVO create(Long userId, BodySaveDTO dto) {
        boolean hasAny = dto.getWeight() != null || dto.getChest() != null
                || dto.getWaist() != null || dto.getHip() != null || dto.getArm() != null;
        if (!hasAny) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "体重和围度至少填一项");
        }

        BodyRecord r = new BodyRecord();
        r.setUserId(userId);
        r.setMeasuredAt(dto.getMeasuredAt() == null ? LocalDate.now() : dto.getMeasuredAt());
        r.setWeight(dto.getWeight());
        r.setChest(dto.getChest());
        r.setWaist(dto.getWaist());
        r.setHip(dto.getHip());
        r.setArm(dto.getArm());
        r.setNote(dto.getNote() == null ? "" : dto.getNote().trim());
        bodyRecordMapper.insert(r);

        BodyRecordVO vo = new BodyRecordVO();
        vo.setId(r.getId());
        vo.setDate(r.getMeasuredAt());
        vo.setWeight(r.getWeight());
        vo.setNote(r.getNote());
        return vo;
    }

    public BodyOverviewVO overview(Long userId) {
        // 升序（同日按提交序），全量在内存组装
        List<BodyRecord> all = bodyRecordMapper.selectList(
                Wrappers.<BodyRecord>lambdaQuery()
                        .eq(BodyRecord::getUserId, userId)
                        .orderByAsc(BodyRecord::getMeasuredAt)
                        .orderByAsc(BodyRecord::getId));

        List<BodyRecord> weights = all.stream().filter(r -> r.getWeight() != null).toList();

        BodyOverviewVO vo = new BodyOverviewVO();
        vo.setIsLowest(false);
        vo.setWeightSeries(new ArrayList<>());

        if (!weights.isEmpty()) {
            BodyRecord latest = weights.get(weights.size() - 1);
            BodyOverviewVO.Latest l = new BodyOverviewVO.Latest();
            l.setDate(latest.getMeasuredAt());
            l.setWeight(latest.getWeight());
            l.setNote(latest.getNote());
            vo.setLatest(l);

            for (BodyRecord r : weights) {
                vo.getWeightSeries().add(new BodyOverviewVO.WeightPoint(r.getMeasuredAt(), r.getWeight()));
            }

            // 「阶段最低」：当前体重就是序列最小值（并列算），且序列 ≥2 条才有「阶段」意义
            BigDecimal min = weights.stream()
                    .map(BodyRecord::getWeight)
                    .min(Comparator.naturalOrder())
                    .orElse(null);
            vo.setIsLowest(weights.size() >= 2 && latest.getWeight().compareTo(min) <= 0);

            // 较上周：与「最新记录日期 −7 天及更早」的最近一条体重之差
            LocalDate cutoff = latest.getMeasuredAt().minusDays(7);
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

        // 最新围度：四围任一非空的最近一条
        all.stream()
                .filter(r -> r.getChest() != null || r.getWaist() != null
                        || r.getHip() != null || r.getArm() != null)
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

        return vo;
    }

    public List<BodyRecordVO> records(Long userId, int limit) {
        // 体重明细：weight 非空。diff＝与时间上相邻「上一条（更早）」之差——正序计算、倒序输出（最新在前）
        List<BodyRecord> all = bodyRecordMapper.selectList(
                Wrappers.<BodyRecord>lambdaQuery()
                        .eq(BodyRecord::getUserId, userId)
                        .isNotNull(BodyRecord::getWeight)
                        .orderByAsc(BodyRecord::getMeasuredAt)
                        .orderByAsc(BodyRecord::getId));

        List<BodyRecordVO> asc = new ArrayList<>();
        BigDecimal prev = null;
        for (BodyRecord r : all) {
            BodyRecordVO vo = new BodyRecordVO();
            vo.setId(r.getId());
            vo.setDate(r.getMeasuredAt());
            vo.setWeight(r.getWeight());
            vo.setNote(r.getNote());
            vo.setDiff(prev == null ? null : r.getWeight().subtract(prev));
            asc.add(vo);
            prev = r.getWeight();
        }

        List<BodyRecordVO> out = new ArrayList<>();
        for (int i = asc.size() - 1; i >= 0 && out.size() < limit; i--) {
            out.add(asc.get(i));
        }
        return out;
    }
}
