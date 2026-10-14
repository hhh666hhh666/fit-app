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
import static org.mockito.Mockito.doReturn;
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
    void recognize_missingSodium_throwsAndLogs() {
        String noSodium = """
                {"name":"缺钠","unit":"g","kj":100,
                 "nutrients":{"protein":1,"fat":1,"carb":1},"nrv":{},"uncertain":[]}
                """;
        FoodRecognitionService s = spyService(noSodium, 100, 20, 0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> s.recognize(7L, mock(MultipartFile.class)));

        assertTrue(ex.getMessage().contains("钠"), ex.getMessage());
        verify(recognitionMapper).insert(any(FoodRecognition.class));
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
