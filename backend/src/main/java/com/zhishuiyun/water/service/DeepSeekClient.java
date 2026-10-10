package com.zhishuiyun.water.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.zhishuiyun.water.common.BusinessException;
import com.zhishuiyun.water.common.ErrorCode;
import com.zhishuiyun.water.config.DeepSeekProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * DeepSeek OpenAI 兼容接口客户端（chat/completions）。
 * 任何服务注入本类后即可通过 {@link #chat} 让 DeepSeek 处理数据。
 */
@Slf4j
@Service
public class DeepSeekClient {

    private final DeepSeekProperties properties;
    private final RestClient restClient;

    public DeepSeekClient(DeepSeekProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    /** 是否已配置可用（未配置 api-key 时返回 false，调用方应回退） */
    public boolean isAvailable() {
        return properties.isConfigured();
    }

    /**
     * 发起一次对话，返回 DeepSeek 生成的文本回答。
     *
     * @param systemPrompt 系统提示词（角色设定 + 数据上下文）
     * @param userPrompt   用户问题
     */
    public String chat(String systemPrompt, String userPrompt) {
        ChatRequest request = new ChatRequest(
                properties.getModel(),
                List.of(new Message("system", systemPrompt), new Message("user", userPrompt)),
                0.3,
                false);
        try {
            RestClient.RequestBodySpec spec = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON);
            // 官方 API 需要 Bearer Key；免费网关通常无需鉴权，仅在有 Key 时才附带
            if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
                spec = spec.header("Authorization", "Bearer " + properties.getApiKey());
            }
            ChatResponse response = spec.body(request).retrieve().body(ChatResponse.class);
            if (response == null || response.choices() == null || response.choices().isEmpty()
                    || response.choices().get(0).message() == null) {
                throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE, "DeepSeek 未返回有效结果");
            }
            String content = response.choices().get(0).message().content();
            return content == null ? "" : content.trim();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 DeepSeek 失败", e);
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE, "调用 DeepSeek 失败：" + e.getMessage());
        }
    }

    public record ChatRequest(String model, List<Message> messages, double temperature, boolean stream) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(String role, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatResponse(List<Choice> choices) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(Message message) {
    }
}
