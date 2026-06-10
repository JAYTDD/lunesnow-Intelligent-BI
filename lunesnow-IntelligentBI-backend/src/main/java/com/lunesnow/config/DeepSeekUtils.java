package com.lunesnow.config;

import cn.hutool.json.JSONUtil;
import com.lunesnow.common.ErrorCode;
import com.lunesnow.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class DeepSeekUtils {

    private static RestTemplate restTemplate;
    private static String apiKey;
    private static String baseUrl;

    public DeepSeekUtils(RestTemplate restTemplate,
                         @Value("${deepseek.api-key}") String apiKey,
                         @Value("${deepseek.base-url}") String baseUrl) {
        DeepSeekUtils.restTemplate = restTemplate;
        DeepSeekUtils.apiKey = apiKey;
        DeepSeekUtils.baseUrl = baseUrl;
    }
    // 调用 DeepSeek AI 模型获取图表
    public static String generateContent(String systemPrompt, String userInput) {
        String url = baseUrl + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        var body = JSONUtil.createObj()
                .set("model", "deepseek-v4-flash")
                .set("stream", false)
                .set("messages", java.util.List.of(
                        java.util.Map.of("role", "system", "content", systemPrompt),
                        java.util.Map.of("role", "user", "content", userInput)
                ));

        HttpEntity<String> request = new HttpEntity<>(body.toString(), headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            if (response.getStatusCode() != HttpStatus.OK) {
                log.error("DeepSeek API error: {}", response.getBody());
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 模型调用失败");
            }

            return JSONUtil.parseObj(response.getBody())
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getStr("content");

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("DeepSeek API call failed", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 模型调用失败");
        }
    }
}
