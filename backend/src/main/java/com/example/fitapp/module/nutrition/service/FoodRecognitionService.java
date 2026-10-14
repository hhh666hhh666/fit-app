package com.example.fitapp.module.nutrition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.fitapp.common.BusinessException;
import com.example.fitapp.common.ResultCode;
import com.example.fitapp.config.RecognitionProperties;
import com.example.fitapp.module.nutrition.dto.FoodSaveDTO;
import com.example.fitapp.module.nutrition.entity.Food;
import com.example.fitapp.module.nutrition.entity.FoodRecognition;
import com.example.fitapp.module.nutrition.mapper.FoodMapper;
import com.example.fitapp.module.nutrition.mapper.FoodRecognitionMapper;
import com.example.fitapp.module.nutrition.vo.FoodDraftVO;
import com.example.fitapp.module.nutrition.vo.RecognitionUsageVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Base64;

/**
 * 营养成分表照片识别（ADR 0018）：照片 → DashScope 多模态 → **草稿（不落库）**。
 *
 * <p>三条铁律在这里落地：</p>
 * <ul>
 *   <li><b>Key 不出服务端</b>：前端只把照片发给本服务，由本服务转发（前端直连＝公开 Key）。</li>
 *   <li><b>模型只照抄</b>：kJ→kcal、NRV% 剔除、每份→每100 全在 {@link LabelDraftParser} 做。</li>
 *   <li><b>失败的调用也记账</b>：token 真实花了；{@code success=0} 与 tokens 一起落流水。</li>
 * </ul>
 * 保存由既有的 {@code POST /api/foods} 完成（用户在校对页确认后走原路径），
 * 所以本服务没有任何写 food 表的代码。
 */
@Service
@RequiredArgsConstructor
public class FoodRecognitionService {

    private static final Logger log = LoggerFactory.getLogger(FoodRecognitionService.class);

    /**
     * 单张图片上限（字节）：multipart 全局已限 10MB，这里再收一档——
     * base64 编码体积涨约 33%，别顶着上游限制传。
     */
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;

    /** 用量明细分页的默认/最大页大小。 */
    private static final int LOG_DEFAULT_SIZE = 50;
    private static final int LOG_MAX_SIZE = 100;

    /**
     * 转录 prompt。要点：**只照抄不换算**（kJ、NRV%、每份数值原样输出）——
     * 转录是 VL 的强项，算术不是；换算与剔除的服务端实现在 LabelDraftParser。
     */
    static final String PROMPT = """
            你是食品营养标签转录器。用户发来一张食品包装照片，请把包装上的「营养成分表」逐字转写成 JSON。

            硬规则：
            1. 只转录照片里实际印出的内容。不做计算、不做估算、不补充。没印的字段一律 null。
            2. 数值原样给出（纯数字，不带单位）。「营养素参考值%」（NRV%）是比例不是绝对值——放进 nrv，严禁写进 nutrients。
            3. 表格按「每份」印时：serving_grams 填一份的克数，nutrients 填每份的数值；按「每100g/100ml」印时：serving_grams 给 null，nutrients 填每100的数值。两种都原样抄，不要换算。
            4. 能量：印 kJ 填 kj，印大卡/kcal 填 kcal，都印就都填。不要自己换算。
            5. name 取包装上的食品名称（最接近品名的一行；不要带品牌口号、广告语、净含量）。
            6. unit：饮品或按毫升计量的填 "ml"，其余填 "g"。
            7. 看不清或不确定的字段名加进 uncertain（如 ["sodium","sugar"]），数值仍给最佳辨认结果，完全没有就 null。
            8. 照片里没有营养成分表时，只输出 {"error":"no_label"}。

            营养行 → JSON 字段名对照（照这个映射，行名对不上就不填）：
            蛋白质→protein｜脂肪→fat｜碳水化合物→carb｜钠→sodium｜饱和脂肪酸→saturatedFat｜糖→sugar｜反式脂肪酸→transFat｜单不饱和脂肪酸→monoFat｜多不饱和脂肪酸→polyFat｜胆固醇→cholesterol｜膳食纤维→fiber｜可溶性膳食纤维→solubleFiber｜不溶性膳食纤维→insolubleFiber｜淀粉→starch｜乳糖→lactose｜维生素A→va｜维生素D→vd｜维生素E→ve｜维生素B1→vb1｜维生素B2→vb2｜维生素B6→vb6｜维生素B12→vb12｜维生素C→vc｜烟酸→niacin｜叶酸→folate｜泛酸→pantothenic｜生物素→biotin｜钙→calcium｜磷→phosphorus｜钾→potassium｜镁→magnesium｜铁→iron｜锌→zinc｜碘→iodine｜硒→selenium｜铜→copper｜锰→manganese｜氟→fluoride｜铬→chromium｜钼→molybdenum｜水分→water｜灰分→ash｜乙醇→alcohol｜有机酸→organicAcid

            只输出 JSON，不要任何解释文字。格式：
            {"name":"","unit":"g","kj":null,"kcal":null,"serving_grams":null,"nutrients":{"protein":null,"fat":null,"carb":null,"sodium":null,"saturatedFat":null,"sugar":null,"transFat":null,"monoFat":null,"polyFat":null,"cholesterol":null,"fiber":null,"solubleFiber":null,"insolubleFiber":null,"starch":null,"lactose":null,"va":null,"vd":null,"ve":null,"vb1":null,"vb2":null,"vb6":null,"vb12":null,"vc":null,"niacin":null,"folate":null,"pantothenic":null,"biotin":null,"calcium":null,"phosphorus":null,"potassium":null,"magnesium":null,"iron":null,"zinc":null,"iodine":null,"selenium":null,"copper":null,"manganese":null,"fluoride":null,"chromium":null,"molybdenum":null,"water":null,"ash":null,"alcohol":null,"organicAcid":null},"nrv":{},"uncertain":[]}
            """;

    private final FoodRecognitionMapper recognitionMapper;
    private final FoodMapper foodMapper;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final RecognitionProperties props;
    private final RestClient restClient;

    /** DashScope 调用的结果：文本内容 ＋ usage（失败时 tokens 为 0，success=0 记账）。 */
    record DashScopeResponse(String content, int promptTokens, int completionTokens, int cachedTokens) {
    }

    /**
     * 识别一张照片 → 草稿（不落库）。无论成败都记一条用量流水。
     */
    public FoodDraftVO recognize(Long userId, MultipartFile file) {
        if (!StringUtils.hasText(props.getApiKey())) {
            // 没配 Key：明确降级（不记账——根本没花 token）
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "识别服务未配置（缺少 API Key），请手动录入");
        }
        long t0 = System.currentTimeMillis();
        int in = 0, out = 0, cached = 0;
        boolean ok = false;
        try {
            DashScopeResponse resp = callVision(file);
            in = resp.promptTokens();
            out = resp.completionTokens();
            cached = resp.cachedTokens();

            LabelDraftParser.Draft draft = LabelDraftParser.parse(resp.content());
            // 名字没认出时照常出草稿（用户回确认页补）——此时跳过 DTO 校验（@NotBlank 会拦
            // 空名）；数值口径解析器已保证，保存时前端与 POST /api/foods 还有双重非空闸。
            if (StringUtils.hasText(draft.dto().getName())) {
                validate(draft.dto());
            }

            // 撞名预检：只看自有（与导入/新建同口径——允许与预置同名）
            Food dup = findDuplicateOwn(userId, draft.dto().getName());
            ok = true;

            FoodDraftVO vo = new FoodDraftVO();
            vo.setDraft(draft.dto());
            vo.setUncertain(new ArrayList<>(draft.uncertain()));
            if (dup != null) {
                vo.setDuplicateFoodId(dup.getId());
                vo.setDuplicateFoodName(dup.getName());
            }
            return vo;
        } catch (BusinessException e) {
            throw e; // 业务性失败（没标签/缺必填/服务不可用）——原样抛给前端
        } catch (Exception e) {
            log.warn("识别失败：{}", e.toString());
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "识别失败，请重试或手动录入");
        } finally {
            logUsage(userId, in, out, cached, (int) (System.currentTimeMillis() - t0), ok);
        }
    }

    /** 用量汇总：「我的」页外卡两值——累计 token（入＋出）＋ 请求次数（含失败）。 */
    public RecognitionUsageVO usage(Long userId) {
        List<Map<String, Object>> rows = recognitionMapper.selectMaps(Wrappers.<FoodRecognition>query()
                .select("COALESCE(SUM(tokens_in + tokens_out), 0) AS total_tokens", "COUNT(*) AS total_requests")
                .eq("user_id", userId));
        Map<String, Object> row = rows.isEmpty() ? Map.of() : rows.get(0);
        RecognitionUsageVO vo = new RecognitionUsageVO();
        vo.setTotalTokens(longOf(row.get("total_tokens")));
        vo.setTotalRequests(longOf(row.get("total_requests")));
        return vo;
    }

    /** 用量明细分页（新的在前）。 */
    public List<FoodRecognition> logs(Long userId, Integer limit, Integer offset) {
        int size = Math.min(Math.max(limit == null ? LOG_DEFAULT_SIZE : limit, 1), LOG_MAX_SIZE);
        int start = Math.max(offset == null ? 0 : offset, 0);
        return recognitionMapper.selectList(Wrappers.<FoodRecognition>lambdaQuery()
                .eq(FoodRecognition::getUserId, userId)
                .orderByDesc(FoodRecognition::getId)
                .last("LIMIT " + start + ", " + size));
    }

    // ── 内部 ──

    /**
     * 调 DashScope（OpenAI 兼容模式）。包成 package-private 是为单测可 spy 掉——
     * 真实 HTTP 不在单元测试里跑（Key 也没有，见 ADR 0018 阻塞项）。
     */
    DashScopeResponse callVision(MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "照片读取失败");
        }
        if (bytes.length == 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "照片是空的");
        }
        if (bytes.length > MAX_IMAGE_BYTES) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "照片超过 5MB，请换一张更小的");
        }
        String mime = file.getContentType() == null ? "image/jpeg" : file.getContentType();
        String dataUri = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);

        JsonNode resp;
        try {
            resp = restClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildRequestBody(dataUri))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            log.warn("DashScope 调用失败：{} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "识别服务暂时不可用，请稍后重试或手动录入");
        }
        if (resp == null) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "识别服务返回为空");
        }
        JsonNode usage = resp.path("usage");
        String content = resp.path("choices").path(0).path("message").path("content").asText(null);
        if (content == null || content.isBlank()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "识别服务没有返回内容，请重试或手动录入");
        }
        return new DashScopeResponse(content,
                usage.path("prompt_tokens").asInt(0),
                usage.path("completion_tokens").asInt(0),
                usage.path("prompt_tokens_details").path("cached_tokens").asInt(0));
    }

    /**
     * 组装请求体。拆成 package-private 方法是为单测——「请求长相」（尤其 thinking 开关）
     * 不该靠真调用验证（Key 也没有，见 ADR 0018 阻塞项）。
     *
     * <p>thinking 参数按 {@code fitapp.recognition.thinking-mode} 三态处理：
     * disabled/enabled 显式传（智谱认；换不认此参数的服务商时配 auto 即可不传），
     * auto 或空值按 disabled 保守处理——本项目场景要的就是「别深思」。</p>
     */
    Map<String, Object> buildRequestBody(String dataUri) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.getModel());
        body.put("messages", List.of(Map.<String, Object>of("role", "user",
                "content", List.of(
                        Map.<String, Object>of("type", "image_url", "image_url", Map.of("url", dataUri)),
                        Map.<String, Object>of("type", "text", "text", PROMPT)))));
        String mode = StringUtils.hasText(props.getThinkingMode())
                ? props.getThinkingMode().trim() : "disabled";
        if ("enabled".equals(mode) || "disabled".equals(mode)) {
            body.put("thinking", Map.of("type", mode));
        }
        return body;
    }

    /** 复用 DTO 上的 Bean Validation（与 JSON 导入同一道闸）。 */
    private void validate(FoodSaveDTO dto) {
        Set<ConstraintViolation<FoodSaveDTO>> violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR,
                    "识别结果不完整：" + violations.iterator().next().getMessage());
        }
    }

    private Food findDuplicateOwn(Long userId, String name) {
        List<Food> hits = foodMapper.selectList(Wrappers.<Food>lambdaQuery()
                .eq(Food::getUserId, userId)
                .eq(Food::getName, name)
                .last("LIMIT 1"));
        return hits.isEmpty() ? null : hits.get(0);
    }

    private void logUsage(Long userId, int in, int out, int cached, int durationMs, boolean success) {
        FoodRecognition row = new FoodRecognition();
        row.setUserId(userId);
        row.setModel(props.getModel());
        row.setTokensIn(in);
        row.setTokensOut(out);
        row.setCachedTokens(cached);
        row.setDurationMs(durationMs);
        row.setSuccess(success);
        recognitionMapper.insert(row);
    }

    private static long longOf(Object v) {
        return v instanceof Number n ? n.longValue() : 0L;
    }
}
