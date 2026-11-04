package com.example.fitapp.module.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.fitapp.common.BusinessException;
import com.example.fitapp.common.PageResult;
import com.example.fitapp.common.ResultCode;
import com.example.fitapp.module.admin.dto.PresetFoodSaveDTO;
import com.example.fitapp.module.admin.vo.AdminFoodRowVO;
import com.example.fitapp.module.admin.vo.ImportPreviewVO;
import com.example.fitapp.module.nutrition.dto.FoodSaveDTO;
import com.example.fitapp.module.nutrition.entity.Food;
import com.example.fitapp.module.nutrition.mapper.FoodMapper;
import com.example.fitapp.module.nutrition.service.FoodJsonReader;
import com.example.fitapp.module.nutrition.service.FoodService;
import com.example.fitapp.module.preset.entity.PresetHidden;
import com.example.fitapp.module.preset.service.PresetHiddenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理端的预置食物（ADR 0048）：列表、单条、增改、下架与上架、分类、导入导出。
 * 只碰 {@code user_id IS NULL} 的行；收藏和浏览次数一律不动。预置以数据库为准（ADR 0049）。
 */
@Service
@RequiredArgsConstructor
public class AdminFoodService {

    public static final String STATUS_LISTED = "listed";
    public static final String STATUS_ARCHIVED = "archived";
    private static final int PAGE_SIZE_MAX = 200;
    private static final int CATEGORY_MAX = 32;

    private final FoodMapper foodMapper;
    private final FoodService foodService;
    private final PresetHiddenService presetHiddenService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    /**
     * @param status {@link #STATUS_LISTED}、{@link #STATUS_ARCHIVED}，其他值＝全部
     * @param sort   {@code updated}＝最近更新在前，其他值＝按名称
     */
    public PageResult<AdminFoodRowVO> page(String q, String category, String status, String sort,
                                           long page, long size) {
        long p = Math.max(page, 1);
        long s = Math.min(Math.max(size, 1), PAGE_SIZE_MAX);
        String kw = q == null ? null : q.trim();
        LambdaQueryWrapper<Food> w = Wrappers.<Food>lambdaQuery()
                .isNull(Food::getUserId)
                .like(StringUtils.hasText(kw), Food::getName, kw)
                .eq(StringUtils.hasText(category), Food::getCategory, category)
                .eq(STATUS_LISTED.equals(status), Food::getArchived, false)
                .eq(STATUS_ARCHIVED.equals(status), Food::getArchived, true);
        if ("updated".equals(sort)) {
            w.orderByDesc(Food::getUpdatedAt);
        } else {
            w.orderByAsc(Food::getName);
        }
        w.orderByAsc(Food::getId);
        Page<Food> result = foodMapper.selectPage(new Page<>(p, s), w);
        List<AdminFoodRowVO> rows = result.getRecords().stream().map(AdminFoodService::toRow).toList();
        return PageResult.of(rows, result.getTotal(), p, s);
    }

    public Food get(Long id) {
        Food food = requirePreset(id);
        food.setPreset(true);
        return food;
    }

    public List<String> categories() {
        return foodMapper.presetCategories();
    }

    /** 同名只提示不拦截（国标数据本身就有重名）：前端在名字栏下给一行提醒。 */
    public boolean nameTaken(String name, Long excludeId) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) {
            return false;
        }
        Long count = foodMapper.selectCount(Wrappers.<Food>lambdaQuery()
                .isNull(Food::getUserId)
                .eq(Food::getName, n)
                .ne(excludeId != null, Food::getId, excludeId));
        return count != null && count > 0;
    }

    public Food create(PresetFoodSaveDTO dto) {
        Food food = newPreset(dto, dto.getCategory());
        foodMapper.insert(food);
        return get(food.getId());
    }

    public Food update(Long id, PresetFoodSaveDTO dto) {
        requirePreset(id);
        writeBack(id, dto, dto.getCategory());
        return get(id);
    }

    /** 下架：没用过的人搜不到，用过的人带「已下架」看到（ADR 0048）。 */
    public void archive(List<Long> ids) {
        List<Long> presets = requirePresets(ids);
        foodMapper.update(null, Wrappers.<Food>lambdaUpdate()
                .in(Food::getId, presets)
                .isNull(Food::getUserId)
                .set(Food::getArchived, true));
    }

    /** 重新上架：所有人恢复可见，用户之前的隐藏连同回收站里的条目一并作废。 */
    @Transactional
    public void unarchive(List<Long> ids) {
        List<Long> presets = requirePresets(ids);
        foodMapper.update(null, Wrappers.<Food>lambdaUpdate()
                .in(Food::getId, presets)
                .isNull(Food::getUserId)
                .set(Food::getArchived, false));
        presetHiddenService.clearAll(PresetHidden.FOOD, presets);
    }

    /** 导出全部预置（含已下架）：带 id 和分类，导回时按 id 更新。archived 只是给人看的，导入时不读。 */
    public List<Map<String, Object>> exportAll() {
        return foodMapper.selectList(Wrappers.<Food>lambdaQuery()
                        .isNull(Food::getUserId)
                        .orderByAsc(Food::getId))
                .stream()
                .map(f -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", f.getId());
                    m.putAll(foodService.toJsonMap(f));
                    if (f.getCategory() != null) {
                        m.put("category", f.getCategory());
                    }
                    m.put("archived", Boolean.TRUE.equals(f.getArchived()));
                    return m;
                })
                .toList();
    }

    /** 预检：只读不写，告诉管理员会新增几条、更新几条、哪些无效。 */
    public ImportPreviewVO previewImport(String text) {
        return toVO(plan(text));
    }

    /** 正式导入：重新预检一遍，有效的在一个事务里写入，出错整批回滚；无效的跳过。上下架状态不动。 */
    @Transactional
    public ImportPreviewVO importText(String text) {
        Plan plan = plan(text);
        for (Planned item : plan.items()) {
            if (item.id() == null) {
                foodMapper.insert(newPreset(item.dto(), item.category()));
            } else {
                writeBack(item.id(), item.dto(), item.category());
            }
        }
        return toVO(plan);
    }

    // ── 导入的预检 ──

    private record Planned(Long id, FoodSaveDTO dto, String category) {
    }

    private record Plan(List<Planned> items, List<ImportPreviewVO.Invalid> invalid) {
    }

    private Plan plan(String text) {
        FoodJsonReader reader = new FoodJsonReader(objectMapper);
        List<JsonNode> nodes = reader.nodes(text);
        Set<Long> presetIds = Set.copyOf(foodMapper.selectObjs(Wrappers.<Food>lambdaQuery()
                        .select(Food::getId)
                        .isNull(Food::getUserId))
                .stream()
                .map(o -> ((Number) o).longValue())
                .toList());
        List<Planned> items = new ArrayList<>();
        List<ImportPreviewVO.Invalid> invalid = new ArrayList<>();
        Set<Long> seenIds = new HashSet<>();
        for (int i = 0; i < nodes.size(); i++) {
            JsonNode node = nodes.get(i);
            int index = i + 1;
            String name = FoodJsonReader.name(node);
            String nameError = FoodJsonReader.nameError(name);
            if (nameError != null) {
                invalid.add(new ImportPreviewVO.Invalid(index, name, nameError));
                continue;
            }
            Long id = null;
            JsonNode idNode = node.get("id");
            if (idNode != null && !idNode.isNull()) {
                if (!idNode.canConvertToLong()) {
                    invalid.add(new ImportPreviewVO.Invalid(index, name, "id 应为整数"));
                    continue;
                }
                id = idNode.asLong();
                if (!presetIds.contains(id)) {
                    invalid.add(new ImportPreviewVO.Invalid(index, name, "id " + id + " 不是现有的预置食物"));
                    continue;
                }
                if (!seenIds.add(id)) {
                    invalid.add(new ImportPreviewVO.Invalid(index, name, "id " + id + " 在文件里出现了不止一次"));
                    continue;
                }
            }
            String category = node.path("category").asText("").trim();
            if (category.isEmpty()) {
                invalid.add(new ImportPreviewVO.Invalid(index, name, "缺少 category"));
                continue;
            }
            if (category.length() > CATEGORY_MAX) {
                invalid.add(new ImportPreviewVO.Invalid(index, name, "category 过长"));
                continue;
            }
            FoodJsonReader.Read read = reader.read(node, name);
            if (read.error() != null) {
                invalid.add(new ImportPreviewVO.Invalid(index, name, read.error()));
                continue;
            }
            // 只校验 Default 组：钠对预置可以不填
            Set<ConstraintViolation<FoodSaveDTO>> violations = validator.validate(read.dto());
            if (!violations.isEmpty()) {
                invalid.add(new ImportPreviewVO.Invalid(index, name, violations.iterator().next().getMessage()));
                continue;
            }
            items.add(new Planned(id, read.dto(), category));
        }
        return new Plan(items, invalid);
    }

    private static ImportPreviewVO toVO(Plan plan) {
        int update = (int) plan.items().stream().filter(p -> p.id() != null).count();
        return new ImportPreviewVO(plan.items().size() - update, update, plan.invalid());
    }

    // ── 写库 ──

    private static Food newPreset(FoodSaveDTO dto, String category) {
        Food food = new Food();
        BeanUtils.copyProperties(dto, food);
        food.setId(null);
        food.setUserId(null);
        food.setName(dto.getName().trim());
        food.setUnit(dto.getUnit() == null || dto.getUnit().isBlank() ? "g" : dto.getUnit());
        food.setCategory(category.trim());
        food.setArchived(false);
        food.setFavorite(null);
        food.setViewCount(null);
        return food;
    }

    /** 整体覆盖营养字段与分类；收藏、浏览次数、下架标记不动。 */
    private void writeBack(Long id, FoodSaveDTO dto, String category) {
        foodMapper.update(null, FoodService.overwrite(Wrappers.<Food>lambdaUpdate()
                        .eq(Food::getId, id)
                        .isNull(Food::getUserId), dto.getName().trim(), dto)
                .set(Food::getCategory, category.trim()));
    }

    private Food requirePreset(Long id) {
        Food food = id == null ? null : foodMapper.selectById(id);
        if (food == null || food.getUserId() != null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "预置食物不存在");
        }
        return food;
    }

    private List<Long> requirePresets(List<Long> ids) {
        List<Long> distinct = ids.stream().distinct().toList();
        Long count = foodMapper.selectCount(Wrappers.<Food>lambdaQuery()
                .in(Food::getId, distinct)
                .isNull(Food::getUserId));
        if (count == null || count != distinct.size()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "选中的条目里有不是预置食物的");
        }
        return distinct;
    }

    private static AdminFoodRowVO toRow(Food f) {
        return new AdminFoodRowVO(f.getId(), f.getName(), f.getCategory(), f.getUnit(),
                f.getKcal(), f.getProtein(), f.getFat(), f.getCarb(), f.getSodium(),
                Boolean.TRUE.equals(f.getArchived()), f.getUpdatedAt());
    }
}
