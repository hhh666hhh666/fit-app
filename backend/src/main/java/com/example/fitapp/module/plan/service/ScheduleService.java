package com.example.fitapp.module.plan.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.fitapp.common.BusinessException;
import com.example.fitapp.common.ResultCode;
import com.example.fitapp.module.plan.dto.ScheduleSetDTO;
import com.example.fitapp.module.plan.entity.Plan;
import com.example.fitapp.module.plan.entity.WeekSchedule;
import com.example.fitapp.module.plan.mapper.PlanMapper;
import com.example.fitapp.module.plan.mapper.WeekScheduleMapper;
import com.example.fitapp.module.plan.vo.ScheduleDayVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 周训练安排：读七天、设置某天（休息 / 指定计划 / 清空）。
 * 存储语义：有行即已安排；无行 = 未安排。设置「未安排」直接删行。
 */
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final WeekScheduleMapper weekScheduleMapper;
    private final PlanMapper planMapper;

    /** 周表全量（固定 7 项，0=周一 … 6=周日）。 */
    public List<ScheduleDayVO> week(Long userId) {
        Map<Integer, WeekSchedule> byDay = weekScheduleMapper.selectList(
                        Wrappers.<WeekSchedule>lambdaQuery().eq(WeekSchedule::getUserId, userId))
                .stream()
                .collect(Collectors.toMap(WeekSchedule::getDayOfWeek, Function.identity(), (a, b) -> a));

        List<ScheduleDayVO> out = new ArrayList<>(7);
        for (int day = 0; day < 7; day++) {
            WeekSchedule r = byDay.get(day);
            out.add(new ScheduleDayVO(
                    day,
                    r == null ? null : r.getPlanId(),
                    r != null && Boolean.TRUE.equals(r.getIsRest())));
        }
        return out;
    }

    @Transactional
    public void set(Long userId, int day, ScheduleSetDTO dto) {
        if (day < 0 || day > 6) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "星期不正确");
        }
        boolean rest = Boolean.TRUE.equals(dto.getIsRest());
        Long planId = dto.getPlanId();

        if (!rest && planId == null) {
            // 清空为「未安排」：删行
            weekScheduleMapper.delete(
                    Wrappers.<WeekSchedule>lambdaQuery()
                            .eq(WeekSchedule::getUserId, userId)
                            .eq(WeekSchedule::getDayOfWeek, day));
            return;
        }
        if (!rest) {
            Plan p = planMapper.selectById(planId);
            if (p == null || !userId.equals(p.getUserId())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "计划不存在");
            }
        }

        WeekSchedule existing = weekScheduleMapper.selectOne(
                Wrappers.<WeekSchedule>lambdaQuery()
                        .eq(WeekSchedule::getUserId, userId)
                        .eq(WeekSchedule::getDayOfWeek, day));
        Long finalPlanId = rest ? null : planId;
        if (existing == null) {
            WeekSchedule row = new WeekSchedule();
            row.setUserId(userId);
            row.setDayOfWeek(day);
            row.setIsRest(rest);
            row.setPlanId(finalPlanId);
            weekScheduleMapper.insert(row);
        } else {
            // 必须用 UpdateWrapper 强写 plan_id=null：全局 not_null 更新策略会静默跳过 null，
            // 否则「计划 → 休息」的切换会留下脏的 plan_id（2026-10 实测坑，见 docs/design/数据模型.md）。
            weekScheduleMapper.update(null, Wrappers.<WeekSchedule>lambdaUpdate()
                    .eq(WeekSchedule::getId, existing.getId())
                    .set(WeekSchedule::getIsRest, rest)
                    .set(WeekSchedule::getPlanId, finalPlanId));
        }
    }
}
