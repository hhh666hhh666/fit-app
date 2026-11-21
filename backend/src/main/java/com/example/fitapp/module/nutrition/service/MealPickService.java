package com.example.fitapp.module.nutrition.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.fitapp.module.nutrition.entity.Food;
import com.example.fitapp.module.nutrition.entity.MealLog;
import com.example.fitapp.module.nutrition.mapper.MealLogMapper;
import com.example.fitapp.module.nutrition.vo.MealPickVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 记一餐弹层的列表（scope #48）：食物库的食物 ＋ 近 30 天吃过的临时条目，按同一套规则排在一起：
 * 分档（完全一样／其余命中，ADR 0058）→ 收藏 → 近 30 天记过几次 → 自建优先。临时条目不能收藏、算作自建。
 *
 * <p>食物先由 SQL 排好（{@link FoodService#searchForPicks}），这里补上每条的分档与次数，
 * 再和临时条目一起做一次稳定排序——几项都相同时食物保持 SQL 的顺序、排在临时条目前面。</p>
 */
@Service
@RequiredArgsConstructor
public class MealPickService {

    private final FoodService foodService;
    private final MealLogMapper mealLogMapper;

    private record Row(MealPickVO vo, int tier, boolean favorite, long count, boolean own) {
    }

    private static final Comparator<Row> ORDER = Comparator.comparingInt(Row::tier)
            .thenComparing(Row::favorite, Comparator.reverseOrder())
            .thenComparing(Row::count, Comparator.reverseOrder())
            .thenComparing(Row::own, Comparator.reverseOrder());

    public List<MealPickVO> picks(Long userId, String q, int limit) {
        limit = Math.min(Math.max(limit, 1), 500);
        FoodSearch terms = FoodSearch.of(q);
        LocalDate since = LocalDate.now(FoodService.ZONE).minusDays(FoodService.RECENT_DAYS - 1L);

        List<Food> foods = foodService.searchForPicks(userId, q, limit);
        Map<Long, Long> counts = foods.isEmpty() ? Map.of()
                : mealLogMapper.countRecentByFood(userId, since, foods.stream().map(Food::getId).toList()).stream()
                .collect(HashMap::new, (m, r) -> m.put(((Number) r.get("fid")).longValue(),
                        ((Number) r.get("cnt")).longValue()), HashMap::putAll);

        List<Row> rows = new ArrayList<>();
        for (Food f : foods) {
            int tier = terms == null ? 0 : terms.tier(f.getName(), f.getAliases(), f.getInitials());
            rows.add(new Row(MealPickVO.ofFood(foodService.toLite(f)), tier, Boolean.TRUE.equals(f.getFavorite()),
                    counts.getOrDefault(f.getId(), 0L), f.getUserId() != null));
        }
        for (MealPickVO.Temp t : temps(userId, since)) {
            if (terms == null) {
                rows.add(new Row(MealPickVO.ofTemp(t), 0, false, t.getCount(), true));
                continue;
            }
            String initials = FoodInitials.of(t.getName(), null);
            if (terms.matches(t.getName(), null, initials)) {
                rows.add(new Row(MealPickVO.ofTemp(t), terms.tier(t.getName(), null, initials), false, t.getCount(), true));
            }
        }
        rows.sort(ORDER);
        return rows.stream().limit(limit).map(Row::vo).toList();
    }

    /** 近 30 天的临时条目，名字和每 100 营养都一样的合成一种；最近记的在前，份量取最近一次的。 */
    List<MealPickVO.Temp> temps(Long userId, LocalDate since) {
        List<MealLog> logs = mealLogMapper.selectList(Wrappers.<MealLog>lambdaQuery()
                .eq(MealLog::getUserId, userId)
                .isNull(MealLog::getFoodId)
                .ge(MealLog::getLogDate, since)
                .orderByDesc(MealLog::getLogDate)
                .orderByDesc(MealLog::getId));
        Map<String, MealPickVO.Temp> groups = new LinkedHashMap<>();
        for (MealLog l : logs) {
            String key = String.join("|", l.getFoodName(), plain(l.getKcalPer100()), plain(l.getProteinPer100()),
                    plain(l.getFatPer100()), plain(l.getCarbPer100()), plain(l.getSodiumPer100()));
            MealPickVO.Temp t = groups.get(key);
            if (t == null) {
                t = new MealPickVO.Temp();
                t.setName(l.getFoodName());
                t.setGrams(l.getGrams());
                t.setKcal(l.getKcalPer100());
                t.setProtein(l.getProteinPer100());
                t.setFat(l.getFatPer100());
                t.setCarb(l.getCarbPer100());
                t.setSodium(l.getSodiumPer100());
                t.setLastDate(l.getLogDate());
                groups.put(key, t);
            }
            t.setCount(t.getCount() + 1);
        }
        return new ArrayList<>(groups.values());
    }

    private static String plain(BigDecimal v) {
        return v == null ? "" : v.stripTrailingZeros().toPlainString();
    }
}
