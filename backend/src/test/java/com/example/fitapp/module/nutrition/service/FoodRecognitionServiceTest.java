package com.example.fitapp.module.nutrition.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.example.fitapp.common.BusinessException;
import com.example.fitapp.config.RecognitionProperties;
import com.example.fitapp.module.nutrition.entity.Food;
import com.example.fitapp.module.nutrition.entity.FoodRecognition;
import com.example.fitapp.module.nutrition.mapper.FoodMapper;
import com.example.fitapp.module.nutrition.mapper.FoodRecognitionMapper;
import com.example.fitapp.module.nutrition.vo.FoodDraftVO;
import com.example.fitapp.module.nutrition.vo.MealDraftVO;
import com.example.fitapp.module.nutrition.vo.RecognitionUsageVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 识别服务的单测（不跑真 HTTP——Key 也没有，见 ADR 0018 阻塞项）：
 * callVision 被 spy 掉，专测口径——无 Key 降级、失败也记账、撞名预检、汇总与分页。
 */
@ExtendWith(MockitoExtension.class)
class FoodRecognitionServiceTest {

    /** lambda 查询立即解析列名，需要 TableInfo 缓存（同 FoodServiceTest）。 */
    @BeforeAll
    static void registerTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Food.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), FoodRecognition.class);
    }

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    private static final String LABEL_JSON = """
            {"name":"全麦面包","unit":"g","kj":1050,
             "nutrients":{"protein":8.2,"fat":3.1,"carb":45.6,"sodium":210,"fiber":5.4},
             "nrv":{"energy":13},"uncertain":["sodium"]}
            """;

    @Mock
    private FoodRecognitionMapper recognitionMapper;

    @Mock
    private FoodMapper foodMapper;

    @Mock
    private RestClient restClient;

    private RecognitionProperties props() {
        RecognitionProperties p = new RecognitionProperties();
        p.setApiKey("test-key");
        return p;
    }

    private FoodRecognitionService service(RecognitionProperties p) {
        return new FoodRecognitionService(
                recognitionMapper, foodMapper, new ObjectMapper(), VALIDATOR, p, restClient);
    }

    /** 真服务 ＋ spy 掉 HTTP 调用（同包，package-private 方法可 stub）。 */
    private FoodRecognitionService spyService(String content, int in, int out, int cached) {
        FoodRecognitionService s = spy(service(props()));
        doReturn(new FoodRecognitionService.DashScopeResponse(content, in, out, cached))
                .when(s).callVision(any());
        return s;
    }

    /** 餐次识别用的 spy：stub 两参的 callVision（餐次识别带自己的 prompt）。 */
    private FoodRecognitionService spyMealService(String content) {
        FoodRecognitionService s = spy(service(props()));
        doReturn(new FoodRecognitionService.DashScopeResponse(content, 2000, 200, 0))
                .when(s).callVision(any(), eq(FoodRecognitionService.MEAL_PROMPT));
        return s;
    }

    @Test
    void recognizeMeal_photo_convertsPortionTotalsToPer100() {
        // 一整份 400g、600 大卡、钠拿不准 → 每 100：150 大卡、蛋白 5、钠 300
        FoodRecognitionService s = spyMealService("""
                {"kind":"photo","name":"番茄炒蛋盖饭","unit":"g","kj":null,"kcal":600,"serving_grams":400,
                 "nutrients":{"protein":20,"fat":24,"carb":80,"sodium":1200},"uncertain":["sodium","serving_grams"]}
                """);

        MealDraftVO vo = s.recognizeMeal(7L, mock(MultipartFile.class));

        assertEquals("photo", vo.getKind());
        assertEquals("番茄炒蛋盖饭", vo.getName());
        assertEquals(0, new BigDecimal("400").compareTo(vo.getGrams()));
        assertEquals(0, new BigDecimal("150").compareTo(vo.getKcal()));
        assertEquals(0, new BigDecimal("5").compareTo(vo.getProtein()));
        assertEquals(0, new BigDecimal("300").compareTo(vo.getSodium()));
        assertEquals(List.of("sodium", "grams"), vo.getUncertain());
        assertTrue(vo.getMissing().isEmpty());
        verify(recognitionMapper).insert(any(FoodRecognition.class));
    }

    @Test
    void recognizeMeal_photoWithoutWeight_fallsBackTo100AndFlagsGrams() {
        FoodRecognitionService s = spyMealService("""
                {"kind":"photo","name":"一碗汤","unit":"ml","kcal":80,"serving_grams":null,
                 "nutrients":{"protein":2,"fat":3,"carb":null,"sodium":null},"uncertain":[]}
                """);

        MealDraftVO vo = s.recognizeMeal(7L, mock(MultipartFile.class));

        assertEquals("ml", vo.getUnit());
        assertEquals(0, new BigDecimal("100").compareTo(vo.getGrams()));
        assertEquals(0, new BigDecimal("80").compareTo(vo.getKcal()));
        assertTrue(vo.getUncertain().contains("grams"));
        // 估不出来的留空，交给用户补
        assertNull(vo.getCarb());
        assertEquals(List.of("carb", "sodium"), vo.getMissing());
    }

    @Test
    void recognizeMeal_labelPer100_hasNoPortion() {
        FoodRecognitionService s = spyMealService("""
                {"kind":"label","name":"全麦面包","unit":"g","kj":1050,"serving_grams":null,
                 "nutrients":{"protein":8.2,"fat":3.1,"carb":45.6,"sodium":210},"uncertain":["kj"]}
                """);

        MealDraftVO vo = s.recognizeMeal(7L, mock(MultipartFile.class));

        assertEquals("label", vo.getKind());
        assertNull(vo.getGrams());
        assertEquals(0, new BigDecimal("250.96").compareTo(vo.getKcal()));
        assertEquals(List.of("kcal"), vo.getUncertain());
    }

    @Test
    void recognizeMeal_notFood_throwsMealWordingAndStillLogs() {
        FoodRecognitionService s = spyMealService("{\"error\":\"not_food\"}");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> s.recognizeMeal(7L, mock(MultipartFile.class)));

        assertTrue(ex.getMessage().contains("没认出食物"), ex.getMessage());
        ArgumentCaptor<FoodRecognition> captor = ArgumentCaptor.forClass(FoodRecognition.class);
        verify(recognitionMapper).insert(captor.capture());
        assertEquals(2000, captor.getValue().getTokensIn());
        assertFalse(captor.getValue().getSuccess());
    }

    @Test
    void mealRequestBody_carriesMealPrompt() {
        Map<String, Object> body = service(props()).buildRequestBody("data:x", FoodRecognitionService.MEAL_PROMPT);
        assertTrue(body.toString().contains("饮食记录助手"));
    }

    @Test
    void recognize_withoutApiKey_throwsAndLogsNothing() {
        RecognitionProperties p = new RecognitionProperties(); // apiKey 空
        MultipartFile file = mock(MultipartFile.class);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service(p).recognize(7L, file));

        assertTrue(ex.getMessage().contains("未配置"), ex.getMessage());
        verify(recognitionMapper, never()).insert(any(FoodRecognition.class));
    }

    @Test
    void recognize_happyPath_returnsDraftAndLogsSuccess() {
        FoodRecognitionService s = spyService(LABEL_JSON, 2400, 300, 0);

        FoodDraftVO vo = s.recognize(7L, mock(MultipartFile.class));

        assertEquals("全麦面包", vo.getDraft().getName());
        assertEquals(0, new BigDecimal("250.96").compareTo(vo.getDraft().getKcal()));
        assertEquals(List.of("sodium"), vo.getUncertain());
        assertNull(vo.getDuplicateFoodId());

        ArgumentCaptor<FoodRecognition> captor = ArgumentCaptor.forClass(FoodRecognition.class);
        verify(recognitionMapper).insert(captor.capture());
        FoodRecognition row = captor.getValue();
        assertEquals(7L, row.getUserId());
        assertEquals("qwen3-vl-8b-instruct", row.getModel());
        assertEquals(2400, row.getTokensIn());
        assertEquals(300, row.getTokensOut());
        assertTrue(row.getSuccess());
    }

    @Test
    void recognize_duplicateName_flagsExistingFood() {
        Food existing = new Food();
        existing.setId(42L);
        existing.setUserId(7L);
        existing.setName("全麦面包");
        when(foodMapper.selectList(any())).thenReturn(List.of(existing));
        FoodRecognitionService s = spyService(LABEL_JSON, 2400, 300, 0);

        FoodDraftVO vo = s.recognize(7L, mock(MultipartFile.class));

        assertEquals(42L, vo.getDuplicateFoodId());
        assertEquals("全麦面包", vo.getDuplicateFoodName());
    }

    @Test
    void recognize_parseFailure_logsZeroSuccessButKeepsTokens() {
        // 模型返回了、token 花了，但内容不是有效数据——必须记 success=0，tokens 照记
        FoodRecognitionService s = spyService("完全不是 JSON", 2400, 300, 120);

        assertThrows(BusinessException.class, () -> s.recognize(7L, mock(MultipartFile.class)));

        ArgumentCaptor<FoodRecognition> captor = ArgumentCaptor.forClass(FoodRecognition.class);
        verify(recognitionMapper).insert(captor.capture());
        FoodRecognition row = captor.getValue();
        assertFalse(row.getSuccess());
        assertEquals(2400, row.getTokensIn());
        assertEquals(300, row.getTokensOut());
        assertEquals(120, row.getCachedTokens());
    }

    @Test
    void recognize_missingSodium_returnsDraftFlaggingMissing() {
        // 标签没印钠：照常出草稿（校验不因空值拦下），缺项点名，记账 success=1
        String noSodium = """
                {"name":"缺钠","unit":"g","kj":100,
                 "nutrients":{"protein":1,"fat":1,"carb":1},"nrv":{},"uncertain":[]}
                """;
        FoodRecognitionService s = spyService(noSodium, 100, 20, 0);

        FoodDraftVO vo = s.recognize(7L, mock(MultipartFile.class));

        assertEquals("缺钠", vo.getDraft().getName());
        assertNull(vo.getDraft().getSodium());
        assertEquals(List.of("sodium"), vo.getMissing());
        ArgumentCaptor<FoodRecognition> captor = ArgumentCaptor.forClass(FoodRecognition.class);
        verify(recognitionMapper).insert(captor.capture());
        assertTrue(captor.getValue().getSuccess(), "出了草稿就算成功");
    }

    @Test
    void usage_sumsTokensAndCounts() {
        when(recognitionMapper.selectMaps(any())).thenReturn(List.of(Map.of(
                "total_tokens", new BigDecimal("5432"),
                "total_requests", 7L)));

        RecognitionUsageVO vo = service(props()).usage(7L);

        assertEquals(5432L, vo.getTotalTokens());
        assertEquals(7L, vo.getTotalRequests());
    }

    @Test
    void recognize_blankName_returnsDraftForManualNaming() {
        // 名字没认出：数值照常出草稿（用户回确认页补名），记账照常 success=1
        String noName = """
                {"name":"","unit":"g","kj":1050,
                 "nutrients":{"protein":8.2,"fat":3.1,"carb":45.6,"sodium":210},
                 "nrv":{},"uncertain":[]}
                """;
        FoodRecognitionService s = spyService(noName, 2400, 300, 0);

        FoodDraftVO vo = s.recognize(7L, mock(MultipartFile.class));

        assertEquals("", vo.getDraft().getName());
        assertTrue(vo.getUncertain().contains("name"));
        assertNull(vo.getDuplicateFoodId());
        ArgumentCaptor<FoodRecognition> captor = ArgumentCaptor.forClass(FoodRecognition.class);
        verify(recognitionMapper).insert(captor.capture());
        assertTrue(captor.getValue().getSuccess(), "出了草稿就算成功");
    }

    @Test
    void recognize_success_recordsFullTrace() {
        FoodRecognitionService s = spy(service(props()));
        doReturn(new FoodRecognitionService.DashScopeResponse(LABEL_JSON, 2400, 300, 100, 120, "stop", "req-1",
                "{\"req\":1}", "{\"resp\":1}"))
                .when(s).callVision(any());
        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(123_456L);
        when(file.getContentType()).thenReturn("image/jpeg");

        s.recognize(7L, file);

        ArgumentCaptor<FoodRecognition> captor = ArgumentCaptor.forClass(FoodRecognition.class);
        verify(recognitionMapper).insert(captor.capture());
        FoodRecognition row = captor.getValue();
        assertEquals("label", row.getKind());
        assertEquals("https://dashscope.aliyuncs.com/compatible-mode/v1", row.getEndpoint());
        assertEquals("disabled", row.getThinkingMode());
        assertNull(row.getReasoningEffort());
        assertEquals(120, row.getReasoningTokens());
        assertEquals(123_456, row.getImageBytes());
        assertEquals("image/jpeg", row.getImageMime());
        assertEquals("stop", row.getFinishReason());
        assertEquals("req-1", row.getUpstreamId());
        assertEquals("全麦面包", row.getResultName());
        assertNull(row.getErrorMessage());
        assertEquals(LABEL_JSON, row.getRawOutput());
        assertEquals("{\"req\":1}", row.getRequestJson());
        assertEquals("{\"resp\":1}", row.getResponseJson());
    }

    @Test
    void recognize_upstreamError_keepsBodyForDetail() {
        FoodRecognitionService s = spy(service(props()));
        doThrow(new FoodRecognitionService.UpstreamFailure("识别服务暂时不可用，请稍后重试或手动录入",
                "{\"model\":\"m\"}", "HTTP 401\n{\"error\":\"invalid api key\"}"))
                .when(s).callVision(any());

        assertThrows(BusinessException.class, () -> s.recognize(7L, mock(MultipartFile.class)));

        ArgumentCaptor<FoodRecognition> captor = ArgumentCaptor.forClass(FoodRecognition.class);
        verify(recognitionMapper).insert(captor.capture());
        FoodRecognition row = captor.getValue();
        assertFalse(row.getSuccess());
        assertEquals("识别服务暂时不可用，请稍后重试或手动录入", row.getErrorMessage());
        assertTrue(row.getResponseJson().contains("invalid api key"));
        assertEquals("{\"model\":\"m\"}", row.getRequestJson());
        assertNull(row.getRawOutput(), "服务商报错时没有模型回答");
        assertNull(row.getResultName());
    }

    @Test
    void recognizeMeal_parseFailure_keepsModelOutputAndReason() {
        FoodRecognitionService s = spyMealService("{\"error\":\"not_food\"}");

        assertThrows(BusinessException.class, () -> s.recognizeMeal(7L, mock(MultipartFile.class)));

        ArgumentCaptor<FoodRecognition> captor = ArgumentCaptor.forClass(FoodRecognition.class);
        verify(recognitionMapper).insert(captor.capture());
        FoodRecognition row = captor.getValue();
        assertEquals("meal", row.getKind());
        assertTrue(row.getErrorMessage().contains("没认出食物"));
        assertEquals("{\"error\":\"not_food\"}", row.getRawOutput());
    }

    @Test
    void logDetail_othersRecord_notFound() {
        FoodRecognition other = new FoodRecognition();
        other.setId(5L);
        other.setUserId(8L);
        when(recognitionMapper.selectById(5L)).thenReturn(other);

        assertThrows(BusinessException.class, () -> service(props()).logDetail(7L, 5L));
    }

    @Test
    void logDetail_own_returnsRow() {
        FoodRecognition mine = new FoodRecognition();
        mine.setId(5L);
        mine.setUserId(7L);
        mine.setRawOutput("{}");
        when(recognitionMapper.selectById(5L)).thenReturn(mine);

        assertEquals("{}", service(props()).logDetail(7L, 5L).getLog().getRawOutput());
    }

    @Test
    void logs_pagedNewestFirst() {
        List<FoodRecognition> rows = service(props()).logs(7L, null, null);

        assertEquals(List.of(), rows);
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FoodRecognition>> captor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(recognitionMapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment().toUpperCase();
        assertTrue(sql.contains("USER_ID"), "应按用户过滤：" + sql);
        assertTrue(sql.contains("ORDER BY"), "应倒序（新的在前）：" + sql);
    }

    // ── 思维链开关（请求长相）：转录场景默认显式关，禁深思 ──

    private static final String DATA_URI = "data:image/jpeg;base64,eHg=";

    @Test
    void requestBody_reasoningEffort_onlySentWhenConfigured() {
        assertFalse(service(props()).buildRequestBody(DATA_URI).containsKey("reasoning_effort"),
                "没配等级就不传——智谱、通义不认这个参数");
        RecognitionProperties p = props();
        p.setThinkingMode("enabled");
        p.setReasoningEffort(" high ");
        Map<String, Object> body = service(p).buildRequestBody(DATA_URI);
        assertEquals("high", body.get("reasoning_effort"));
        assertEquals(Map.of("type", "enabled"), body.get("thinking"));
    }

    @Test
    void requestBody_thinkingDisabledByDefault() {
        Map<String, Object> body = service(props()).buildRequestBody(DATA_URI);

        assertEquals(Map.of("type", "disabled"), body.get("thinking"));
    }

    @Test
    void requestBody_thinkingEnabled_passesThrough() {
        RecognitionProperties p = props();
        p.setThinkingMode("enabled");

        assertEquals(Map.of("type", "enabled"), service(p).buildRequestBody(DATA_URI).get("thinking"));
    }

    @Test
    void requestBody_thinkingAuto_omitsParam() {
        RecognitionProperties p = props();
        p.setThinkingMode("auto");

        assertFalse(service(p).buildRequestBody(DATA_URI).containsKey("thinking"),
                "auto 不该传 thinking——按服务商默认");
    }

    @Test
    void requestBody_thinkingBlank_treatedAsDisabled() {
        RecognitionProperties p = props();
        p.setThinkingMode("  ");

        assertEquals(Map.of("type", "disabled"), service(p).buildRequestBody(DATA_URI).get("thinking"));
    }
}
