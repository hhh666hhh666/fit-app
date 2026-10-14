package com.example.fitapp.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 识别用的 RestClient（Boot 3.5 自带，零新依赖）＋ 注册识别配置属性。
 * 独立出来只为两件事：① 集中 baseUrl；② 测试可注掉换 mock。
 */
@Configuration
@EnableConfigurationProperties(RecognitionProperties.class)
public class RecognitionClientConfig {

    @Bean
    public RestClient recognitionRestClient(RestClient.Builder builder, RecognitionProperties props) {
        return builder.baseUrl(props.getEndpoint()).build();
    }
}
