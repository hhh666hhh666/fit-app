package com.example.fitapp.module.nutrition.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.fitapp.common.BusinessException;
import com.example.fitapp.common.ResultCode;
import com.example.fitapp.module.nutrition.dto.ComboSaveDTO;
import com.example.fitapp.module.nutrition.dto.FoodAmountDTO;
import com.example.fitapp.module.nutrition.entity.Food;
import com.example.fitapp.module.nutrition.entity.FoodCombo;
import com.example.fitapp.module.nutrition.entity.FoodComboItem;
import com.example.fitapp.module.nutrition.mapper.FoodComboItemMapper;
import com.example.fitapp.module.nutrition.mapper.FoodComboMapper;
import com.example.fitapp.module.nutrition.mapper.FoodMapper;
import com.example.fitapp.module.nutrition.vo.ComboVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 食物组合：一串「食物 ＋ 固定克数」的集合（ADR 0012）。
 * 组合只是"批量添加的快捷键"——加入一餐由 {@link DietService#addMealsBatch} 展开为多条普通 meal_log，
 * 本服务只管组合自身的读写。明细**整体覆盖**（编辑即全量替换）；
 * 食物弱引用、**不存营养快照**：整份热量按 food **现值**现算，食物已被删的条目仍列出（靠名字快照）但不计入合计。
 */
@Service
@RequiredArgsConstructor
public class ComboService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final FoodComboMapper comboMapper;
    private final FoodComboItemMapper itemMapper;
    private final FoodMapper foodMapper;

    /** 列表：按名称排序（同值按 id，保证顺序稳定）。 */
    public List<ComboVO> list(Long userId) {
        List<FoodCombo> combos = comboMapper.selectList(Wrappers.<FoodCombo>lambdaQuery()
                .eq(FoodCombo::getUserId, userId)
                .orderByAsc(FoodCombo::getName)
                .orderByAsc(FoodCombo::getId));
        if (combos.isEmpty()) {
            return List.of();
        }
        List<Long> comboIds = combos.stream().map(FoodCombo::getId).toList();
        List<FoodComboItem> allItems = itemMapper.selectList(Wrappers.<FoodComboItem>lambdaQuery()
                .in(FoodComboItem::getComboId, comboIds)
                .orderByAsc(FoodComboItem::getSortOrder)
                .orderByAsc(FoodComboItem::getId));
        Map<Long, Food> foods = loadFoods(allItems);
        Map<Long, List<FoodComboItem>> byCombo = allItems.stream()
                .collect(Collectors.groupingBy(FoodComboItem::getComboId, LinkedHashMap::new, Collectors.toList()));
        return combos.stream()
                .map(c -> toVO(c, byCombo.getOrDefault(c.getId(), List.of()), foods))
                .toList();
    }

    public ComboVO get(Long userId, Long id) {
        FoodCombo combo = requireOwn(userId, id);
        List<FoodComboItem> items = itemMapper.selectList(Wrappers.<FoodComboItem>lambdaQuery()
                .eq(FoodComboItem::getComboId, id)
                .orderByAsc(FoodComboItem::getSortOrder)
                .orderByAsc(FoodComboItem::getId));
        return toVO(combo, items, loadFoods(items));
    }

    @Transactional
    public ComboVO create(Long userId, ComboSaveDTO dto) {
        String name = dto.getName().trim();
        requireNameFree(userId, name, null);
        FoodCombo combo = new FoodCombo();
        combo.setUserId(userId);
        combo.setName(name);
        comboMapper.insert(combo);
        writeItems(combo.getId(), userId, dto.getItems());
        return get(userId, combo.getId());
    }

    @Transactional
    public ComboVO update(Long userId, Long id, ComboSaveDTO dto) {
        requireOwn(userId, id);
        String name = dto.getName().trim();
        requireNameFree(userId, name, id);
        // 组合名整体覆盖（显式 set，避开全局 not_null 策略）
        comboMapper.update(null, Wrappers.<FoodCombo>lambdaUpdate()
                .eq(FoodCombo::getId, id)
                .eq(FoodCombo::getUserId, userId)
                .set(FoodCombo::getName, name));
        // 明细整体覆盖：先清后写
        itemMapper.delete(Wrappers.<FoodComboItem>lambdaQuery().eq(FoodComboItem::getComboId, id));
        writeItems(id, userId, dto.getItems());
        return get(userId, id);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        requireOwn(userId, id);
        itemMapper.delete(Wrappers.<FoodComboItem>lambdaQuery().eq(FoodComboItem::getComboId, id));
        comboMapper.deleteById(id);
    }

    // ── 辅助 ──

    /** 明细落库：名字按 food 现值取快照。同一食物允许在一份组合里出现多次（不强求去重）。 */
    private void writeItems(Long comboId, Long userId, List<FoodAmountDTO> items) {
        int order = 0;
        for (FoodAmountDTO it : items) {
            Food food = requireFood(userId, it.getFoodId());
            FoodComboItem row = new FoodComboItem();
            row.setComboId(comboId);
            row.setFoodId(food.getId());
            row.setFoodName(food.getName());
            row.setGrams(it.getGrams());
            row.setSortOrder(order++);
            itemMapper.insert(row);
        }
    }

    /** 一次批量取齐涉及的食物，避免逐条查询。 */
    private Map<Long, Food> loadFoods(List<FoodComboItem> items) {
        List<Long> foodIds = items.stream()
                .map(FoodComboItem::getFoodId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (foodIds.isEmpty()) {
            return Map.of();
        }
        return foodMapper.selectBatchIds(foodIds).stream()
                .collect(Collectors.toMap(Food::getId, f -> f));
    }

    private ComboVO toVO(FoodCombo combo, List<FoodComboItem> items, Map<Long, Food> foods) {
        ComboVO vo = new ComboVO();
        vo.setId(combo.getId());
        vo.setName(combo.getName());
        BigDecimal total = BigDecimal.ZERO;
        List<ComboVO.Item> out = new ArrayList<>();
        for (FoodComboItem row : items) {
            ComboVO.Item i = new ComboVO.Item();
            i.setFoodId(row.getFoodId());
            i.setFoodName(row.getFoodName());
            i.setGrams(row.getGrams());
            // 食物已被删（弱引用置空）：仍列出名字，但没有单位与热量、不计入合计
            Food food = row.getFoodId() == null ? null : foods.get(row.getFoodId());
            if (food != null) {
                i.setUnit(food.getUnit());
                BigDecimal kcal = food.getKcal() == null ? BigDecimal.ZERO
                        : food.getKcal().multiply(row.getGrams()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
                i.setKcal(kcal);
                total = total.add(kcal);
            }
            out.add(i);
        }
        vo.setItems(out);
        vo.setTotalKcal(total.setScale(2, RoundingMode.HALF_UP));
        return vo;
    }

    private FoodCombo requireOwn(Long userId, Long id) {
        FoodCombo combo = comboMapper.selectById(id);
        if (combo == null || !userId.equals(combo.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "组合不存在");
        }
        return combo;
    }

    private Food requireFood(Long userId, Long foodId) {
        Food food = foodMapper.selectById(foodId);
        // 与记录口径一致：系统预置（user_id 为空）也可入组合
        if (food == null || (food.getUserId() != null && !userId.equals(food.getUserId()))) {
            throw new BusinessException(ResultCode.NOT_FOUND, "食物不存在");
        }
        return food;
    }

    private void requireNameFree(Long userId, String name, Long excludeId) {
        Long count = comboMapper.selectCount(Wrappers.<FoodCombo>lambdaQuery()
                .eq(FoodCombo::getUserId, userId)
                .eq(FoodCombo::getName, name)
                .ne(excludeId != null, FoodCombo::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "已有同名组合");
        }
    }
}
