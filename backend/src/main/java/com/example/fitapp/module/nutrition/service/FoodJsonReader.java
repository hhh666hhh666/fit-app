package com.example.fitapp.module.nutrition.service;

import com.example.fitapp.common.BusinessException;
import com.example.fitapp.common.ResultCode;
import com.example.fitapp.module.nutrition.dto.FoodSaveDTO;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 食物 JSON 的读法（snake_case；kcal 或 kj 二选一，给 kJ 自动换算；常用份是嵌套的 {label, grams}）。
 * App 的自建食物导入和管理端的预置导入共用这一份，格式只有一种。
 * 只管「读成 DTO」，查重和 Bean Validation 由调用方各自决定。
 */
public final class FoodJsonReader {

    private static final BigDecimal KJ_PER_KCAL = new BigDecimal("4.184");
    private static final Set<String> UNITS = Set.of("g", "ml");
    public static final int NAME_MAX = 64;

    /** 读成功时 error 为空；失败时 dto 为空，error 是给人看的原因。 */
    public record Read(FoodSaveDTO dto, String error) {
    }

    private final ObjectMapper snakeReader;

    public FoodJsonReader(ObjectMapper objectMapper) {
        this.snakeReader = objectMapper.copy()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /** 顶层可以是数组，也可以是单个对象。 */
    public List<JsonNode> nodes(String text) {
        JsonNode root;
        try {
            root = snakeReader.readTree(text);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "JSON 格式不正确");
        }
        List<JsonNode> nodes = new ArrayList<>();
        if (root != null && root.isArray()) {
            root.forEach(nodes::add);
        } else if (root != null && root.isObject()) {
            nodes.add(root);
        } else {
            throw new BusinessException(ResultCode.BAD_REQUEST, "JSON 顶层应为数组");
        }
        return nodes;
    }

    /** 去掉首尾空白的名字；没有就是空串。 */
    public static String name(JsonNode node) {
        return node.path("name").asText("").trim();
    }

    /** 名字本身的问题（缺失、过长）；没问题返回 null。 */
    public static String nameError(String name) {
        if (name.isEmpty()) {
            return "缺少 name";
        }
        if (name.length() > NAME_MAX) {
            return "name 过长";
        }
        return null;
    }

    /** 把一个对象读成 DTO；name 用调用方已经取好的那一份。 */
    public Read read(JsonNode node, String name) {
        FoodSaveDTO item;
        try {
            item = snakeReader.treeToValue(node, FoodSaveDTO.class);
        } catch (Exception e) {
            return new Read(null, "字段类型不正确");
        }
        item.setName(name);

        // 能量：kcal 或 kj（二选一；给 kJ 自动换算）
        if (item.getKcal() == null && node.hasNonNull("kj")) {
            JsonNode kj = node.get("kj");
            if (!kj.isNumber()) {
                return new Read(null, "kj 应为数字");
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
                    return new Read(null, "portion.grams 应为数字");
                }
                item.setPortionGrams(new BigDecimal(grams.asText()));
            }
        }
        // unit 显式写错则失败；缺省按 g
        if (item.getUnit() != null && !UNITS.contains(item.getUnit())) {
            return new Read(null, "unit 只能是 g 或 ml");
        }
        return new Read(item, null);
    }
}
