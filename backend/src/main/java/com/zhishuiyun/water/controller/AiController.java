package com.zhishuiyun.water.controller;

import com.zhishuiyun.water.common.ApiResponse;
import com.zhishuiyun.water.security.CurrentUser;
import com.zhishuiyun.water.service.AiQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiQueryService aiQueryService;

    @PostMapping("/query")
    public ApiResponse<Map<String, String>> query(@RequestBody Map<String, String> body) {
        String question = body.getOrDefault("question", "");
        String answer = aiQueryService.query(question, CurrentUser.required());
        return ApiResponse.ok(Map.of("answer", answer));
    }
}
