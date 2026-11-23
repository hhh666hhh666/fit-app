package com.example.fitapp.module.nutrition.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.fitapp.module.nutrition.entity.Food;
import com.example.fitapp.module.nutrition.dto.PickHideDTO;
import com.example.fitapp.module.nutrition.entity.MealLog;
import com.example.fitapp.module.nutrition.entity.MealPickHidden;
import com.example.fitapp.module.nutrition.mapper.MealLogMapper;
import com.example.fitapp.module.nutrition.mapper.MealPickHiddenMapper;
import com.example.fitapp.module.nutrition.vo.MealPickVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 记一餐弹层的列表（scope #48）：食物库的食物 ＋ 近 30 天吃过的临时条目，按同一套规则排在一起：
 * 分档（完全一样／其余命中，ADR 0058）→ 收藏 → 最后一次记进餐里的时间，新的在前（ADR 0064）→ 自定义优先。
 * 临时条目不能收藏、算作自定义。
 *
 * <p>食物先由 SQL 排好（{@link FoodService#searchForPicks}），这里补上每条的分档与最后一次使用时间，
 * 再和临时条目一起做一次稳定排序——几项都相同时食物保持 SQL 的顺序、排在临时条目前面。</p>
 */
@Service
@RequiredArgsConstructor
public class MealPickService {

    private final FoodService foodService;
    private final MealLogMapper mealLogMapper;
    private final MealPickHiddenMapper hiddenMapper;

    /** lastUsed 为 null＝没记过，排在记过的后面。 */
    private record Row(MealPickVO vo, int tier, boolean favorite, LocalDateTime lastUsed, boolean own) {
    }

    private static final Comparator<Row> ORDER = Comparator.comparingInt(Row::tier)
            .thenComparing(Row::favorite, Comparator.reverseOrder())
            .thenComparing(Row::lastUsed, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(Row::own, Comparator.reverseOrder());

    public List<MealPickVO> picks(Long userId, String q, int limit) {
        limit = Math.min(Math.max(limit, 1), 500);
        FoodSearch terms = FoodSearch.of(q);
        LocalDate since = LocalDate.now(FoodService.ZONE).minusDays(FoodService.RECENT_DAYS - 1L);

        List<Food> foods = foodService.searchForPicks(userId, q, limit);
        Map<Long, LocalDateTime> lastUsed = new HashMap<>();
        if (!foods.isEmpty()) {
            for (Map<String, Object> r : mealLogMapper.lastUsedByFood(userId, foods.stream().map(Food::getId).toList())) {
                LocalDateTime t = toDateTime(r.get("last"));
                if (t != null) {
                    lastUsed.put(((Number) r.get("fid")).longValue(), t);
                }
            }
        }

        List<Row> rows = new ArrayList<>();
        for (Food f : foods) {
            int tier = terms == null ? 0 : terms.tier(f.getName(), f.getAliases(), f.getInitials());
            rows.add(new Row(MealPickVO.ofFood(foodService.toLite(f)), tier, Boolean.TRUE.equals(f.getFavorite()),
                    lastUsed.get(f.getId()), f.getUserId() != null));
        }
        for (TempGroup g : temps(userId, since)) {
            MealPickVO.Temp t = g.temp();
            if (terms == null) {
                rows.add(new Row(MealPickVO.ofTemp(t), 0, false, g.lastUsed(), true));
                continue;
            }
            String initials = FoodInitials.of(t.getName(), null);
            if (terms.matches(t.getName(), null, initials)) {
                rows.add(new Row(MealPickVO.ofTemp(t), terms.tier(t.getName(), null, initials), false, g.lastUsed(), true));
            }
        }
        rows.sort(ORDER);
        return rows.stream().limit(limit).map(Row::vo).toList();
    }

    /** 一种临时条目和它最后一次被记下的时间。 */
    record TempGroup(MealPickVO.Temp temp, LocalDateTime lastUsed) {
    }

    /** JDBC 驱动给 DATETIME 的类型随版本不同：LocalDateTime 或 Timestamp。 */
    private static LocalDateTime toDateTime(Object v) {
        if (v instanceof LocalDateTime t) {
            return t;
        }
        if (v instanceof Timestamp t) {
            return t.toLocalDateTime();
        }
        return null;
    }

    /**
     * 从记一餐列表里移除一种吃过的临时条目（已经记下的不动）。再移除一次只是把时刻往后挪；
     * 移除之后又记了同样的一条，它会重新出现（见 {@link #temps}）。
     */
    public void hide(Long userId, PickHideDTO dto) {
        String key = keyOf(dto.getName().trim(), dto.getKcal(), dto.getProtein(), dto.getFat(), dto.getCarb(),
                dto.getSodium());
        LocalDateTime now = LocalDateTime.now(FoodService.ZONE);
        MealPickHidden h = hiddenMapper.selectOne(Wrappers.<MealPickHidden>lambdaQuery()
                .eq(MealPickHidden::getUserId, userId)
                .eq(MealPickHidden::getPickKey, key));
        if (h == null) {
            h = new MealPickHidden();
            h.setUserId(userId);
            h.setPickKey(key);
            h.setHiddenAt(now);
            hiddenMapper.insert(h);
        } else {
            h.setHiddenAt(now);
            hiddenMapper.updateById(h);
        }
    }

    /** 一种临时条目的键：名字 ＋ 每 100 营养（去尾零），列表合并与移除都按它认。 */
    static String keyOf(String name, BigDecimal kcal, BigDecimal protein, BigDecimal fat, BigDecimal carb,
                        BigDecimal sodium) {
        return String.join("|", name, plain(kcal), plain(protein), plain(fat), plain(carb), plain(sodium));
    }

    /** 近 30 天的临时条目，名字和每 100 营养都一样的合成一种；最近记的在前，份量取最近一次的。 */
    List<TempGroup> temps(Long userId, LocalDate since) {
        List<MealLog> logs = mealLogMapper.selectList(Wrappers.<MealLog>lambdaQuery()
                .eq(MealLog::getUserId, userId)
                .isNull(MealLog::getFoodId)
                .ge(MealLog::getLogDate, since)
                .orderByDesc(MealLog::getLogDate)
                .orderByDesc(MealLog::getId));
        Map<String, MealPickVO.Temp> groups = new LinkedHashMap<>();
        Map<String, LocalDateTime> last = new HashMap<>();
        for (MealLog l : logs) {
            String key = keyOf(l.getFoodName(), l.getKcalPer100(), l.getProteinPer100(), l.getFatPer100(),
                    l.getCarbPer100(), l.getSodiumPer100());
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
            LocalDateTime c = l.getCreatedAt();
            if (c != null && (last.get(key) == null || c.isAfter(last.get(key)))) {
                last.put(key, c);
            }
        }
        Map<String, LocalDateTime> hidden = new HashMap<>();
        if (!groups.isEmpty()) {
            for (MealPickHidden h : hiddenMapper.selectList(Wrappers.<MealPickHidden>lambdaQuery()
                    .eq(MealPickHidden::getUserId, userId)
                    .in(MealPickHidden::getPickKey, groups.keySet()))) {
                hidden.put(h.getPickKey(), h.getHiddenAt());
            }
        }
        List<TempGroup> out = new ArrayList<>();
        groups.forEach((k, t) -> {
            LocalDateTime h = hidden.get(k);
            LocalDateTime l = last.get(k);
            // 移除之后没再记过：不出现；移除之后又记了一条：重新出现
            if (h != null && (l == null || !l.isAfter(h))) {
                return;
            }
            out.add(new TempGroup(t, l));
        });
        return out;
    }

    private static String plain(BigDecimal v) {
        return v == null ? "" : v.stripTrailingZeros().toPlainString();
    }
}
