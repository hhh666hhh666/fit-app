package com.example.fitapp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 营养成分表照片识别配置（ADR 0018）。
 *
 * 端点走 DashScope（百炼）的 OpenAI 兼容模式；模型档默认 8b（自用标签转录够用），
 * 不够时改 {@code model} 再拿测试集验证即可。api-key 为空＝未配置：识别接口返回
 * 友好业务错误（降级），不影响其他功能。
 */
@Data
@ConfigurationProperties(prefix = "fitapp.recognition")
public class RecognitionProperties {

    /** OpenAI 兼容 base url（不含 /chat/completions）。 */
    private String endpoint = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    /** 模型档。qwen3-vl-8b-instruct 起步；准确率不够再升档。 */
    private String model = "qwen3-vl-8b-instruct";

    /** DashScope API Key。生产环境经 FITAPP_RECOGNITION_API_KEY 注入（值只存在于服务器 .env）。 */
    private String apiKey = "";

    /**
     * 思维链开关：disabled=显式关闭（默认）；enabled=显式开启；auto/空=不传该参数（按服务商默认）。
     *
     * 为什么默认关：智谱的 thinking.type 默认 enabled（模型自行判断要不要深思），
     * 而本功能是「照抄」——转录是 VL 的强项，深思只加延迟、把思考 token 烧成输出计费，
     * 还可能诱发 GLM-4.6V 已知的 overthinking（复杂 prompt 下过度推理、重复输出）。
     */
    private String thinkingMode = "disabled";

    /**
     * 思考等级，原样作为 {@code reasoning_effort} 传给服务商（DeepSeek 认 none / low / high / max）；
     * 空＝不传（默认——智谱、通义不认这个参数）。只在开着思考时才有意义：DeepSeek 的 thinking 与它同时给时以 thinking 为准，
     * 所以要用等级就把 thinking-mode 配成 enabled 或 auto。
     */
    private String reasoningEffort = "";
}
