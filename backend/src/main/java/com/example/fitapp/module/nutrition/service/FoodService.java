package com.example.fitapp.module.nutrition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.fitapp.common.BusinessException;
import com.example.fitapp.common.ResultCode;
import com.example.fitapp.module.nutrition.dto.FoodSaveDTO;
import com.example.fitapp.module.nutrition.entity.Food;
import com.example.fitapp.module.nutrition.mapper.FoodMapper;
import com.example.fitapp.module.nutrition.vo.ImportResultVO;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 食物库：列表（收藏置顶 → 浏览次数降序 → 名称）/ 单条增改删 / 浏览次数 +1 / 收藏标记 /
 * JSON 文本导入 / 导出。
 * 库内两种来源：**系统预置**（user_id 为空，只读，可「复制为自定义」）＋ **用户自建**；
 * 列表/搜索两者合并返回；编辑为**整体覆盖**（空值要能清掉，走显式 set——全局 not_null 会跳过 null）。
 */
@Service
@RequiredArgsConstructor
public class FoodService {

    private static final BigDecimal KJ_PER_KCAL = new BigDecimal("4.184");
    private static final Set<String> UNITS = Set.of("g", "ml");
    /** 轻量列表的条数上限（防一次性拉全库）。 */
    private static final int LITE_MAX_LIMIT = 500;

    private final FoodMapper foodMapper;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final FoodTrash foodTrash;

    /** 列表/搜索：预置 + 自有合并，按浏览次数降序（常用上浮，同值按名称）。 */
    public List<Food> list(Long userId, String q) {
        List<Food> foods = foodMapper.selectList(baseQuery(userId, q));
        foods.forEach(this::fillPreset);
        return foods;
    }

    /**
     * 列表轻量模式：只带列表与记录选择所需字段（配 Jackson non_null，其余字段不输出）。
     * limit/offset 用于滚动分页（食物库页每页 100 条）；limit 为空表示不分页。
     */
    public List<Food> listLite(Long userId, String q, Integer limit, Integer offset) {
        LambdaQueryWrapper<Food> w = baseQuery(userId, q);
        if (limit != null) {
            int size = Math.min(Math.max(limit, 1), LITE_MAX_LIMIT);
            int start = offset == null ? 0 : Math.max(offset, 0);
            w.last("LIMIT " + start + ", " + size);
        }
        return foodMapper.selectList(w).stream().map(this::toLite).toList();
    }

    /** 浏览次数 +1（详情页查看时调用）。预置与自有均可浏览；不存在或他人私有 → 404。 */
    public void incrementView(Long userId, Long id) {
        Food food = foodMapper.selectById(id);
        if (food == null || (food.getUserId() != null && !userId.equals(food.getUserId()))) {
            throw new BusinessException(ResultCode.NOT_FOUND, "食物不存在");
        }
        foodMapper.incrementViewCount(id);
    }

    /** 收藏 / 取消收藏（收藏＝列表置顶）。预置与自有均可收藏；不存在或他人私有 → 404。 */
    public void setFavorite(Long userId, Long id, boolean favorite) {
        Food food = foodMapper.selectById(id);
        if (food == null || (food.getUserId() != null && !userId.equals(food.getUserId()))) {
            throw new BusinessException(ResultCode.NOT_FOUND, "食物不存在");
        }
        foodMapper.update(null, Wrappers.<Food>lambdaUpdate()
                .eq(Food::getId, id)
                .set(Food::getFavorite, favorite));
    }

    /** 单条（预置与自有均可读）：编辑回填、复制为自定义、详情查看用。 */
    public Food get(Long userId, Long id) {
        Food food = foodMapper.selectById(id);
        if (food == null || (food.getUserId() != null && !userId.equals(food.getUserId()))) {
            throw new BusinessException(ResultCode.NOT_FOUND, "食物不存在");
        }
        fillPreset(food);
        return food;
    }

    /** 数量统计（预置＋自有，可按关键词过滤）：供「我的」页显示总数、食物库滚动分页判断是否到底。 */
    public Map<String, Long> count(Long userId, String q) {
        long preset = foodMapper.selectCount(scopeQuery(null, q));
        long mine = foodMapper.selectCount(scopeQuery(userId, q));
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("total", preset + mine);
        m.put("preset", preset);
        m.put("mine", mine);
        return m;
    }

    private LambdaQueryWrapper<Food> scopeQuery(Long ownerId, String q) {
        String kw = q == null ? null : q.trim();
        LambdaQueryWrapper<Food> w = Wrappers.<Food>lambdaQuery()
                .like(StringUtils.hasText(kw), Food::getName, kw);
        if (ownerId == null) {
            w.isNull(Food::getUserId);
        } else {
            w.eq(Food::getUserId, ownerId);
        }
        return w;
    }

    private LambdaQueryWrapper<Food> baseQuery(Long userId, String q) {
        String kw = q == null ? null : q.trim();
        return Wrappers.<Food>lambdaQuery()
                .and(w -> w.eq(Food::getUserId, userId).or().isNull(Food::getUserId))
                .like(StringUtils.hasText(kw), Food::getName, kw)
                // 排序：① 收藏置顶 ② 个人常用度（浏览次数降序）③ 名称、id 升序（保证分页稳定）
                .orderByDesc(Food::getFavorite)
                .orderByDesc(Food::getViewCount)
                .orderByAsc(Food::getName)
                .orderByAsc(Food::getId);
    }

    /** 只查自有（不含预置）：导出、导入查重专用。 */
    private List<Food> ownList(Long userId) {
        return foodMapper.selectList(Wrappers.<Food>lambdaQuery()
                .eq(Food::getUserId, userId)
                .orderByAsc(Food::getName)
                .orderByAsc(Food::getId));
    }

    public Food create(Long userId, FoodSaveDTO dto) {
        String name = dto.getName().trim();
        requireNameFree(userId, name, null);
        Food food = new Food();
        BeanUtils.copyProperties(dto, food);
        food.setUserId(userId);
        food.setName(name);
        food.setUnit(normalizeUnit(dto.getUnit()));
        foodMapper.insert(food);
        food.setPreset(false);
        return food;
    }

    public Food update(Long userId, Long id, FoodSaveDTO dto) {
        requireOwn(userId, id);
        String name = dto.getName().trim();
        requireNameFree(userId, name, id);
        // ⚠️ 整体覆盖：可选字段要能被清空。全局 update-strategy=not_null，updateById 会静默跳过 null，
        //    所以这里用 LambdaUpdateWrapper 显式 set（含 null）。
        foodMapper.update(null, Wrappers.<Food>lambdaUpdate()
                .eq(Food::getId, id)
                .eq(Food::getUserId, userId)
                .set(Food::getName, name)
                .set(Food::getUnit, normalizeUnit(dto.getUnit()))
                .set(Food::getKcal, dto.getKcal())
                .set(Food::getProtein, dto.getProtein())
                .set(Food::getFat, dto.getFat())
                .set(Food::getCarb, dto.getCarb())
                .set(Food::getSodium, dto.getSodium())
                .set(Food::getSaturatedFat, dto.getSaturatedFat())
                .set(Food::getSugar, dto.getSugar())
                .set(Food::getTransFat, dto.getTransFat())
                .set(Food::getMonoFat, dto.getMonoFat())
                .set(Food::getPolyFat, dto.getPolyFat())
                .set(Food::getCholesterol, dto.getCholesterol())
                .set(Food::getFiber, dto.getFiber())
                .set(Food::getSolubleFiber, dto.getSolubleFiber())
                .set(Food::getInsolubleFiber, dto.getInsolubleFiber())
                .set(Food::getStarch, dto.getStarch())
                .set(Food::getLactose, dto.getLactose())
                .set(Food::getGi, dto.getGi())
                .set(Food::getVa, dto.getVa())
                .set(Food::getVd, dto.getVd())
                .set(Food::getVe, dto.getVe())
                .set(Food::getVb1, dto.getVb1())
                .set(Food::getVb2, dto.getVb2())
                .set(Food::getVb6, dto.getVb6())
                .set(Food::getVb12, dto.getVb12())
                .set(Food::getVc, dto.getVc())
                .set(Food::getNiacin, dto.getNiacin())
                .set(Food::getFolate, dto.getFolate())
                .set(Food::getPantothenic, dto.getPantothenic())
                .set(Food::getBiotin, dto.getBiotin())
                .set(Food::getCalcium, dto.getCalcium())
                .set(Food::getPhosphorus, dto.getPhosphorus())
                .set(Food::getPotassium, dto.getPotassium())
                .set(Food::getMagnesium, dto.getMagnesium())
                .set(Food::getIron, dto.getIron())
                .set(Food::getZinc, dto.getZinc())
                .set(Food::getIodine, dto.getIodine())
                .set(Food::getSelenium, dto.getSelenium())
                .set(Food::getCopper, dto.getCopper())
                .set(Food::getManganese, dto.getManganese())
                .set(Food::getFluoride, dto.getFluoride())
                .set(Food::getChromium, dto.getChromium())
                .set(Food::getMolybdenum, dto.getMolybdenum())
                .set(Food::getWater, dto.getWater())
                .set(Food::getAsh, dto.getAsh())
                .set(Food::getAlcohol, dto.getAlcohol())
                .set(Food::getOrganicAcid, dto.getOrganicAcid())
                .set(Food::getPortionLabel, dto.getPortionLabel())
                .set(Food::getPortionGrams, dto.getPortionGrams()));
        Food updated = foodMapper.selectById(id);
        fillPreset(updated);
        return updated;
    }

    @Transactional
    public void delete(Long userId, Long id) {
        foodTrash.put(userId, requireOwn(userId, id));
        foodMapper.deleteById(id);
    }

    /** 导出全库：只导**自有**（预置系统自带、无需备份）；与导入对称的 snake_case 结构（只含非空字段）。 */
    public List<Map<String, Object>> exportAll(Long userId) {
        return ownList(userId).stream().map(this::toJsonMap).toList();
    }

    /**
     * 粘贴 JSON 导入：数组（或单对象）→ 逐条校验 → 入库。
     * 校验规则：name 必填且 ≤64；4+1 必填（kcal 可由 kj 换算）；数值非负；unit ∈ {g, ml}；
     * 同名（库内已有或批内重复）跳过并报告。
     */
    public ImportResultVO importText(Long userId, String text) {
        JsonNode root;
        try {
            root = objectMapper.readTree(text);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "JSON 格式不正确");
        }
        List<JsonNode> nodes = new ArrayList<>();
        if (root.isArray()) {
            root.forEach(nodes::add);
        } else if (root.isObject()) {
            nodes.add(root);
        } else {
            throw new BusinessException(ResultCode.BAD_REQUEST, "JSON 顶层应为数组");
        }

        ObjectMapper snakeReader = objectMapper.copy()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // 查重只看自有：允许与预置同名（预置是系统数据，用户可基于同名建自己的版本）
        Set<String> seen = ownList(userId).stream().map(Food::getName).collect(Collectors.toSet());
        ImportResultVO result = new ImportResultVO();

        for (JsonNode node : nodes) {
            String name = node.path("name").asText("").trim();
            if (name.isEmpty()) {
                result.getFailed().add(new ImportResultVO.Failed("（未命名）", "缺少 name"));
                continue;
            }
            if (name.length() > 64) {
                result.getFailed().add(new ImportResultVO.Failed(name, "name 过长"));
                continue;
            }
            if (seen.contains(name)) {
                result.getSkipped().add(name);
                continue;
            }

            FoodSaveDTO item;
            try {
                item = snakeReader.treeToValue(node, FoodSaveDTO.class);
            } catch (Exception e) {
                result.getFailed().add(new ImportResultVO.Failed(name, "字段类型不正确"));
                continue;
            }
            item.setName(name);

            // 能量：kcal 或 kj（二选一；给 kJ 自动换算）
            if (item.getKcal() == null && node.hasNonNull("kj")) {
                JsonNode kj = node.get("kj");
                if (!kj.isNumber()) {
                    result.getFailed().add(new ImportResultVO.Failed(name, "kj 应为数字"));
                    continue;
                }
                item.setKcal(new BigDecimal(kj.asText()).divide(KJ_PER_KCAL, 2, RoundingMode.HALF_UP));
            }
            // 常用份：嵌套对象 {label, grams}
            JsonNode portion = node.path("portion");
            if (portion.isObject()) {
                item.setPortionLabel(portion.path("label").asText(null));
                if (portion.hasNonNull("grams")) {
                    JsonNode grams = portion.get("grams");
                    if (!grams.isNumber()) {
                        result.getFailed().add(new ImportResultVO.Failed(name, "portion.grams 应为数字"));
                        continue;
                    }
                    item.setPortionGrams(new BigDecimal(grams.asText()));
                }
            }
            // unit 显式写错则失败；缺省按 g
            if (item.getUnit() != null && !UNITS.contains(item.getUnit())) {
                result.getFailed().add(new ImportResultVO.Failed(name, "unit 只能是 g 或 ml"));
                continue;
            }

            // 复用 DTO 上的 Bean Validation 注解（4+1 必填、非负、长度等）
            Set<ConstraintViolation<FoodSaveDTO>> violations = validator.validate(item);
            if (!violations.isEmpty()) {
                result.getFailed().add(new ImportResultVO.Failed(name,
                        violations.iterator().next().getMessage()));
                continue;
            }

            Food food = new Food();
            BeanUtils.copyProperties(item, food);
            food.setUserId(userId);
            food.setName(name);
            food.setUnit(normalizeUnit(item.getUnit()));
            foodMapper.insert(food);
            seen.add(name);
            result.setImported(result.getImported() + 1);
        }
        return result;
    }

    public Map<String, Object> toJsonMap(Food f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", f.getName());
        m.put("unit", f.getUnit());
        putIfNotNull(m, "kcal", f.getKcal());
        putIfNotNull(m, "protein", f.getProtein());
        putIfNotNull(m, "fat", f.getFat());
        putIfNotNull(m, "carb", f.getCarb());
        putIfNotNull(m, "sodium", f.getSodium());
        putIfNotNull(m, "saturated_fat", f.getSaturatedFat());
        putIfNotNull(m, "sugar", f.getSugar());
        putIfNotNull(m, "trans_fat", f.getTransFat());
        putIfNotNull(m, "mono_fat", f.getMonoFat());
        putIfNotNull(m, "poly_fat", f.getPolyFat());
        putIfNotNull(m, "cholesterol", f.getCholesterol());
        putIfNotNull(m, "fiber", f.getFiber());
        putIfNotNull(m, "soluble_fiber", f.getSolubleFiber());
        putIfNotNull(m, "insoluble_fiber", f.getInsolubleFiber());
        putIfNotNull(m, "starch", f.getStarch());
        putIfNotNull(m, "lactose", f.getLactose());
        putIfNotNull(m, "gi", f.getGi());
        putIfNotNull(m, "va", f.getVa());
        putIfNotNull(m, "vd", f.getVd());
        putIfNotNull(m, "ve", f.getVe());
        putIfNotNull(m, "vb1", f.getVb1());
        putIfNotNull(m, "vb2", f.getVb2());
        putIfNotNull(m, "vb6", f.getVb6());
        putIfNotNull(m, "vb12", f.getVb12());
        putIfNotNull(m, "vc", f.getVc());
        putIfNotNull(m, "niacin", f.getNiacin());
        putIfNotNull(m, "folate", f.getFolate());
        putIfNotNull(m, "pantothenic", f.getPantothenic());
        putIfNotNull(m, "biotin", f.getBiotin());
        putIfNotNull(m, "calcium", f.getCalcium());
        putIfNotNull(m, "phosphorus", f.getPhosphorus());
        putIfNotNull(m, "potassium", f.getPotassium());
        putIfNotNull(m, "magnesium", f.getMagnesium());
        putIfNotNull(m, "iron", f.getIron());
        putIfNotNull(m, "zinc", f.getZinc());
        putIfNotNull(m, "iodine", f.getIodine());
        putIfNotNull(m, "selenium", f.getSelenium());
        putIfNotNull(m, "copper", f.getCopper());
        putIfNotNull(m, "manganese", f.getManganese());
        putIfNotNull(m, "fluoride", f.getFluoride());
        putIfNotNull(m, "chromium", f.getChromium());
        putIfNotNull(m, "molybdenum", f.getMolybdenum());
        putIfNotNull(m, "water", f.getWater());
        putIfNotNull(m, "ash", f.getAsh());
        putIfNotNull(m, "alcohol", f.getAlcohol());
        putIfNotNull(m, "organic_acid", f.getOrganicAcid());
        if (f.getPortionLabel() != null && f.getPortionGrams() != null) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("label", f.getPortionLabel());
            p.put("grams", f.getPortionGrams());
            m.put("portion", p);
        }
        return m;
    }

    private static void putIfNotNull(Map<String, Object> m, String key, Object v) {
        if (v != null) {
            m.put(key, v);
        }
    }

    private static String normalizeUnit(String unit) {
        return (unit == null || unit.isBlank()) ? "g" : unit;
    }

    private void requireNameFree(Long userId, String name, Long excludeId) {
        Long count = foodMapper.selectCount(Wrappers.<Food>lambdaQuery()
                .eq(Food::getUserId, userId)
                .eq(Food::getName, name)
                .ne(excludeId != null, Food::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "已有同名食物");
        }
    }

    private Food requireOwn(Long userId, Long id) {
        Food food = foodMapper.selectById(id);
        if (food == null || (food.getUserId() != null && !userId.equals(food.getUserId()))) {
            throw new BusinessException(ResultCode.NOT_FOUND, "食物不存在");
        }
        if (food.getUserId() == null) {
            // 系统预置：只读（与动作库预设同规则）；要微调走「复制为自定义」。
            throw new BusinessException(ResultCode.FORBIDDEN, "预置食物不可修改或删除");
        }
        return food;
    }

    private void fillPreset(Food f) {
        if (f != null) {
            f.setPreset(f.getUserId() == null);
        }
    }

    /** 轻量视图：只留列表/记录选择所需字段，其余置空（Jackson non_null 下不输出）。 */
    private Food toLite(Food f) {
        Food lite = new Food();
        lite.setId(f.getId());
        lite.setName(f.getName());
        lite.setUnit(f.getUnit());
        lite.setCategory(f.getCategory());
        lite.setViewCount(f.getViewCount());
        lite.setFavorite(f.getFavorite());
        lite.setKcal(f.getKcal());
        lite.setProtein(f.getProtein());
        lite.setFat(f.getFat());
        lite.setCarb(f.getCarb());
        lite.setSodium(f.getSodium());
        lite.setGi(f.getGi());
        lite.setPortionLabel(f.getPortionLabel());
        lite.setPortionGrams(f.getPortionGrams());
        lite.setPreset(f.getUserId() == null);
        return lite;
    }
}
