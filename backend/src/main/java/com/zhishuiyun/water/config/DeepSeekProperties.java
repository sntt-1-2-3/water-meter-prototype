package com.zhishuiyun.water.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DeepSeek 配置，对应 application.yml 的 deepseek.*。
 * 通过环境变量 DEEPSEEK_ENABLED / DEEPSEEK_API_KEY / DEEPSEEK_MODEL 等覆盖。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "deepseek")
public class DeepSeekProperties {

    /** 是否启用 DeepSeek（官方 API 或免费网关）；未启用时 AI 助手回退到本地规则引擎 */
    private boolean enabled = false;

    /** API Key。官方 API 必需；使用免费网关时留空（若网关配置了 TFG_API_KEY 则填对应值） */
    private String apiKey = "";

    /** OpenAI 兼容接口地址：官方 https://api.deepseek.com，或免费网关 http://localhost:3456/v1 */
    private String baseUrl = "https://api.deepseek.com";

    /** 模型：deepseek-chat（对话）或 deepseek-reasoner（推理） */
    private String model = "deepseek-chat";

    /** 请求超时（秒） */
    private int timeoutSeconds = 60;

    public boolean isConfigured() {
        return enabled;
    }
}
