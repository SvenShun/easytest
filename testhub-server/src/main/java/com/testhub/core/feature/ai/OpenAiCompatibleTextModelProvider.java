package com.testhub.core.feature.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testhub.common.web.ApiException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible chat completions provider (openai / deepseek). One retry is
 * attempted when the response cannot be parsed into structured BDD drafts.
 */
@Component
public class OpenAiCompatibleTextModelProvider implements TextModelProvider {

    private static final String SYSTEM_PROMPT = """
            你是 TestHub 的测试设计助手。请根据需求生成 1-3 条结构化的客户端 BDD 用例。
            只返回 JSON，不要输出 Markdown。JSON 结构为：
            {"drafts":[{"title":"用例标题","given":"前置条件","when":"操作步骤","then":"预期结果"}]}
            每个字段都必须是非空的中文文本。
            """;

    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public OpenAiCompatibleTextModelProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(30_000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public boolean supports(String provider) {
        return "openai".equalsIgnoreCase(provider) || "deepseek".equalsIgnoreCase(provider);
    }

    @Override
    public List<BddDraft> generate(TextModelInput input) {
        RuntimeException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return requestDrafts(input);
            } catch (RuntimeException e) {
                last = e;
            }
        }
        throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_GENERATION_FAILED",
                "模型返回无法解析: " + (last == null ? "unknown" : last.getMessage()));
    }

    private List<BddDraft> requestDrafts(TextModelInput input) {
        String endpoint = resolveEndpoint(input);
        Map<String, Object> userPrompt = new LinkedHashMap<>();
        userPrompt.put("requirement", input.requirementText());
        if (input.samples() != null && !input.samples().isEmpty()) {
            userPrompt.put("approvedSamples", input.samples());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", input.modelId());
        body.put("temperature", 0.2);
        body.put("messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", writeJson(userPrompt))));
        String response = restClient.post()
                .uri(endpoint + "/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + nullToEmpty(input.apiKey()))
                .body(body)
                .retrieve()
                .body(String.class);
        return parse(response);
    }

    private List<BddDraft> parse(String response) {
        if (response == null || response.isBlank()) {
            throw new IllegalArgumentException("empty response");
        }
        try {
            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").path(0).path("message").path("content").asText(null);
            if (content == null || content.isBlank()) {
                throw new IllegalArgumentException("missing message content");
            }
            JsonNode draftsNode = objectMapper.readTree(extractJson(content)).path("drafts");
            if (!draftsNode.isArray() || draftsNode.isEmpty()) {
                throw new IllegalArgumentException("missing drafts array");
            }
            List<BddDraft> drafts = new ArrayList<>();
            for (JsonNode node : draftsNode) {
                String title = text(node, "title");
                String given = text(node, "given");
                String when = text(node, "when");
                String then = text(node, "then");
                if (title == null || given == null || when == null || then == null) {
                    throw new IllegalArgumentException("draft fields must not be blank");
                }
                drafts.add(new BddDraft(title, given, when, then));
                if (drafts.size() >= 3) {
                    break;
                }
            }
            return drafts;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private String extractJson(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private String resolveEndpoint(TextModelInput input) {
        String endpoint = input.endpoint();
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = "deepseek".equalsIgnoreCase(input.provider())
                    ? "https://api.deepseek.com" : "https://api.openai.com/v1";
        }
        return endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("构建模型请求失败", e);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return null;
        }
        return value.asText().trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
