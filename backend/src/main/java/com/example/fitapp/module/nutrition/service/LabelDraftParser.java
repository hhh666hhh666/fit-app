package com.example.fitapp.module.nutrition.service;

import com.example.fitapp.common.BusinessException;
import com.example.fitapp.common.ResultCode;
import com.example.fitapp.module.nutrition.dto.FoodSaveDTO;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 视觉模型输出 → 食物草稿 的纯解析（ADR 0018）。无 Spring 依赖，可单测。
 *
 * <p>核心口径：模型只做**照抄**（能量 kJ、NRV% 原样输出），换算与剔除都在本类做——
 * 转录是 VL 的强项，算术不是。两个「 silent killer 」在这里被显式拆掉：</p>
 * <ul>
 *   <li><b>kJ→kcal</b>：国标标签印 kJ，食物库存 kcal（÷4.184，与 FoodService 同口径）。</li>
 *   <li><b>NRV%</b>：标签右侧「营养素参考值%」是比例不是绝对值——prompt 要求模型把它
 *       放进 {@code nrv} 字段，本类**永不读取**，从结构上杜绝混入。</li>
 * </ul>
 * 按份印的标签（每份 X g）：模型给每份数值＋serving_grams，本类换算成每 100。
 */
public final class LabelDraftParser {

    /** kJ→kcal 除数。与 FoodService.KJ_PER_KCAL 同值——物理常量，两处各持一份。 */
    private static final BigDecimal KJ_PER_KCAL = new BigDecimal("4.184");

    /** 每份 → 每100 的基准倍率。 */
    private static final BigDecimal PER_100 = new BigDecimal("100");

    /**
     * 标签上可能出现的营养行 → 食物字段 key（GB 28050 为主，兼容进口标签常见行）。
     * GI 不在内——营养成分表不印 GI，用户要的话手工补。
     */
    public static final List<String> NUTRIENT_KEYS = List.of(
            "protein", "fat", "carb", "sodium",
            "saturatedFat", "sugar", "transFat", "monoFat", "polyFat", "cholesterol",
            "fiber", "solubleFiber", "insolubleFiber", "starch", "lactose",
            "va", "vd", "ve", "vb1", "vb2", "vb6", "vb12", "vc", "niacin", "folate",
            "pantothenic", "biotin",
            "calcium", "phosphorus", "potassium", "magnesium", "iron", "zinc", "iodine",
            "selenium", "copper", "manganese", "fluoride", "chromium", "molybdenum",
            "water", "ash", "alcohol", "organicAcid");

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private LabelDraftParser() {
    }

    /** 解析结果：草稿 ＋ 低置信度字段 key 列表。 */
    public record Draft(FoodSaveDTO dto, List<String> uncertain) {
    }

    /**
     * @param content 模型返回的文本（可能是裸 JSON，也可能包了 ```json 围栏）
     */
    public static Draft parse(String content) {
        JsonNode root = readTree(content);

        // 模型明确报告「没有营养成分表」
        if (root.path("error").isTextual()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "照片里没找到营养成分表，请重试或手动录入");
        }

        String name = root.path("name").asText("").trim();
        if (name.length() > 64) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "食物名过长（超过 64 字），请手动录入");
        }
        // 名字没认出**不再抛错**：数值照常出草稿，用户回确认页自己补（保存时前端与
        // POST /api/foods 的 @NotBlank 双重把关）。下面 4+1 齐全才放行。

        String unit = "ml".equals(root.path("unit").asText("")) ? "ml" : "g";
        BigDecimal factor = per100Factor(root.path("serving_grams"));

        // ── 组装规范化节点，再一次性绑到 DTO（字段名与 DTO 一一对应，新增字段零改动）──
        ObjectNode normalized = MAPPER.createObjectNode();
        normalized.put("name", name);
        normalized.put("unit", unit);
        BigDecimal kcal = energy(root, factor);
        if (kcal != null) {
            normalized.put("kcal", kcal);
        }
        // nrv 字段被显式丢弃：比例不是数值，永不进 DTO
        normalized.setAll(scaledNutrients(root.path("nutrients"), factor));

        FoodSaveDTO dto = MAPPER.convertValue(normalized, FoodSaveDTO.class);

        List<String> missing = new ArrayList<>();
        if (dto.getKcal() == null) missing.add("能量");
        if (dto.getProtein() == null) missing.add("蛋白质");
        if (dto.getFat() == null) missing.add("脂肪");
        if (dto.getCarb() == null) missing.add("碳水化合物");
        if (dto.getSodium() == null) missing.add("钠");
        if (!missing.isEmpty()) {
            // 名字也没有 ＋ 4+1 全缺 ＝ 模型对「没有标签的图」回了全 null 骨架——
            // 给「没找到营养成分表」的准话，而不是罗列五个缺失项。
            if (name.isEmpty() && missing.size() == 5) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "照片里没能认出营养成分表，请手动录入");
            }
            throw new BusinessException(ResultCode.BUSINESS_ERROR,
                    "标签上看不到：" + String.join("、", missing) + "——请手动录入");
        }
        List<String> uncertain = uncertainKeys(root.path("uncertain"));
        if (name.isEmpty()) {
            uncertain.add("name"); // 名字缺失也标「待核对」——确认页会提示用户补
        }
        return new Draft(dto, uncertain);
    }

    /** 每份 → 每100 倍率；按 100 印（或没给）时为 1。非正数视为没给。 */
    private static BigDecimal per100Factor(JsonNode servingGrams) {
        if (!servingGrams.isNumber()) {
            return BigDecimal.ONE;
        }
        BigDecimal g = servingGrams.decimalValue();
        if (g.signum() <= 0) {
            return BigDecimal.ONE;
        }
        return PER_100.divide(g, 4, RoundingMode.HALF_UP);
    }

    /** 能量：优先 kcal 直读；否则 kJ 换算（都按「每份/每100」的 factor 先归位再除）。 */
    private static BigDecimal energy(JsonNode root, BigDecimal factor) {
        JsonNode kcal = root.path("kcal");
        if (kcal.isNumber() && kcal.decimalValue().signum() >= 0) {
            return kcal.decimalValue().multiply(factor).setScale(2, RoundingMode.HALF_UP);
        }
        JsonNode kj = root.path("kj");
        if (kj.isNumber() && kj.decimalValue().signum() > 0) {
            return kj.decimalValue().multiply(factor).divide(KJ_PER_KCAL, 2, RoundingMode.HALF_UP);
        }
        return null;
    }

    /** 营养数值：只认数字，统一归位到每 100 并保留 2 位小数；负数是脏数据，当场拦。 */
    private static ObjectNode scaledNutrients(JsonNode nutrients, BigDecimal factor) {
        ObjectNode out = MAPPER.createObjectNode();
        if (!nutrients.isObject()) {
            return out;
        }
        nutrients.fields().forEachRemaining(e -> {
            JsonNode v = e.getValue();
            if (!v.isNumber()) {
                return; // 非数字（含 null）跳过——没印的就是没印
            }
            BigDecimal n = v.decimalValue();
            if (n.signum() < 0) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR,
                        "识别到异常负值（" + e.getKey() + "），请重试或手动录入");
            }
            out.put(e.getKey(), n.multiply(factor).setScale(2, RoundingMode.HALF_UP));
        });
        return out;
    }

    /** 低置信度字段：只认已知 key（模型有时会把标签行名塞进来），去重保序。 */
    private static List<String> uncertainKeys(JsonNode uncertain) {
        Set<String> known = new LinkedHashSet<>(NUTRIENT_KEYS);
        known.add("name");
        Set<String> out = new LinkedHashSet<>();
        if (uncertain.isArray()) {
            uncertain.forEach(k -> {
                String key = k.asText("");
                if (known.contains(key)) {
                    out.add(key);
                }
            });
        }
        return new ArrayList<>(out);
    }

    /** 容错读取：剥掉 ```json 围栏；不是 JSON 就明确报错（不猜）。 */
    private static JsonNode readTree(String content) {
        String s = content == null ? "" : content.trim();
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceAll("```\\s*$", "").trim();
        }
        try {
            return MAPPER.readTree(s);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "识别结果不是有效数据，请重试或手动录入");
        }
    }
}
