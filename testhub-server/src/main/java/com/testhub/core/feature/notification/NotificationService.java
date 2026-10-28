package com.testhub.core.feature.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testhub.common.security.CurrentUser;
import com.testhub.common.util.Ids;
import com.testhub.common.web.ApiException;
import com.testhub.core.feature.notification.NotificationDtos.ChannelRequest;
import com.testhub.core.feature.notification.NotificationDtos.ChannelView;
import com.testhub.core.feature.notification.NotificationDtos.TestResult;
import com.testhub.core.feature.plan.PlanRun;
import com.testhub.core.feature.plan.RunStatus;
import com.testhub.core.feature.plan.TestPlan;
import com.testhub.core.feature.plan.TestPlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Webhook robots for run completion notifications (Sonic alert-robot parity). */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final Set<String> TYPES = Set.of("GENERIC", "DINGTALK", "WECOM", "FEISHU");
    private static final String EVENT = "RUN_COMPLETED";

    private final NotificationChannelRepository channels;
    private final TestPlanRepository plans;
    private final ObjectMapper objectMapper;
    private final HttpClient http;
    private final String publicBaseUrl;

    public NotificationService(NotificationChannelRepository channels,
                               TestPlanRepository plans, ObjectMapper objectMapper,
                               @Value("${testhub.public-base-url:http://127.0.0.1:8080}") String publicBaseUrl) {
        this.channels = channels;
        this.plans = plans;
        this.objectMapper = objectMapper;
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.replaceAll("/+$", "");
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    public List<ChannelView> list() {
        return channels.findAllByOrderByCreatedAtDesc().stream().map(this::view).toList();
    }

    @Transactional
    public ChannelView create(ChannelRequest request) {
        validate(request);
        Instant now = Instant.now();
        NotificationChannel channel = new NotificationChannel(Ids.newId("notify"), request.name().trim(),
                normalizeType(request.channelType()), request.webhookUrl().trim(),
                blankToNull(request.secret()), request.enabled() == null || request.enabled(),
                normalizeEvents(request.events()), CurrentUser.require().username(), now, now);
        return view(channels.save(channel));
    }

    @Transactional
    public ChannelView update(String id, ChannelRequest request) {
        validate(request);
        NotificationChannel channel = require(id);
        channel.setName(request.name().trim());
        channel.setChannelType(normalizeType(request.channelType()));
        channel.setWebhookUrl(request.webhookUrl().trim());
        // Blank means "keep existing secret"; UI has no clear-secret action.
        if (request.secret() != null && !request.secret().isBlank()) {
            channel.setSecret(request.secret().trim());
        }
        channel.setEnabled(request.enabled() == null || request.enabled());
        channel.setEvents(normalizeEvents(request.events()));
        channel.setUpdatedAt(Instant.now());
        return view(channels.save(channel));
    }

    @Transactional
    public void delete(String id) {
        channels.delete(require(id));
    }

    /** Sends a sample payload and records the result on the channel. */
    @Transactional
    public TestResult test(String id) {
        NotificationChannel channel = require(id);
        String text = "[TestHub] 通知机器人测试\n这是一条来自 TestHub 的测试消息。";
        Map<String, Object> payload = Map.of(
                "event", "TEST",
                "message", text,
                "timestamp", Instant.now().toString());
        try {
            send(channel, payload, text);
            channel.setLastTestAt(Instant.now());
            channel.setLastTestStatus("OK");
            channels.save(channel);
            return new TestResult(true, "发送成功", channel.getLastTestAt());
        } catch (Exception e) {
            channel.setLastTestAt(Instant.now());
            channel.setLastTestStatus(truncate(e.getMessage(), 300));
            channels.save(channel);
            throw ApiException.badRequest("发送失败: " + e.getMessage());
        }
    }

    /**
     * Called by PlanRunService after recomputing counters. Marks the run as notified so repeated
     * reads cannot send duplicate messages, then sends asynchronously.
     */
    public void onRunFinished(PlanRun run) {
        if (run == null || run.isNotificationSent() || run.getFinishedAt() == null
                || run.getStatus() == RunStatus.RUNNING) {
            return;
        }
        run.setNotificationSent(true);
        // Avoid re-notifying historical runs when a view triggers a recompute after upgrade.
        if (run.getFinishedAt().isBefore(Instant.now().minusSeconds(3600))) {
            return;
        }
        TestPlan plan = plans.findById(run.getPlanId()).orElse(null);
        String planName = plan == null ? run.getPlanId() : plan.getName();
        String status = statusText(run.getStatus());
        String text = String.format("[TestHub] 计划「%s」执行%s\n运行：%s\n总数 %d / 成功 %d / 失败 %d / 超时 %d\n%s",
                planName, status, run.getId(), run.getTotal(), run.getSuccess(), run.getFailed(),
                run.getTimeout(), publicBaseUrl + "/#/runs/" + run.getId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", EVENT);
        payload.put("runId", run.getId());
        payload.put("planId", run.getPlanId());
        payload.put("planName", planName);
        payload.put("status", run.getStatus().name());
        payload.put("total", run.getTotal());
        payload.put("success", run.getSuccess());
        payload.put("failed", run.getFailed());
        payload.put("timeout", run.getTimeout());
        payload.put("startedAt", run.getStartedAt() == null ? null : run.getStartedAt().toString());
        payload.put("finishedAt", run.getFinishedAt().toString());
        payload.put("url", publicBaseUrl + "/#/runs/" + run.getId());
        payload.put("text", text);

        for (NotificationChannel channel : channels.findByEnabledTrue()) {
            if (channel.getEvents() != null && !channel.getEvents().contains(EVENT)) {
                continue;
            }
            CompletableFuture.runAsync(() -> {
                try {
                    send(channel, payload, text);
                } catch (Exception e) {
                    log.warn("通知发送失败 channel={} type={}: {}", channel.getId(), channel.getChannelType(),
                            e.getMessage());
                }
            });
        }
    }

    private void send(NotificationChannel channel, Map<String, Object> genericPayload, String text) throws Exception {
        String type = normalizeType(channel.getChannelType());
        URI uri = URI.create(channel.getWebhookUrl());
        Map<String, Object> body = switch (type) {
            case "DINGTALK" -> Map.of("msgtype", "markdown",
                    "markdown", (Object) Map.of("title", "TestHub 执行通知", "text", text));
            case "WECOM" -> Map.of("msgtype", "markdown",
                    "markdown", (Object) Map.of("content", text));
            case "FEISHU" -> Map.of("msg_type", "text",
                    "content", (Object) Map.of("text", text));
            default -> genericPayload;
        };
        if ("DINGTALK".equals(type) && channel.getSecret() != null && !channel.getSecret().isBlank()) {
            uri = URI.create(withDingTalkSign(channel.getWebhookUrl(), channel.getSecret()));
        }
        String json = objectMapper.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + ": " + truncate(response.body(), 200));
        }
    }

    private String withDingTalkSign(String url, String secret) throws Exception {
        long timestamp = System.currentTimeMillis();
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String sign = URLEncoder.encode(Base64.getEncoder().encodeToString(
                mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8))), StandardCharsets.UTF_8);
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + "timestamp=" + timestamp + "&sign=" + sign;
    }

    private void validate(ChannelRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw ApiException.badRequest("机器人名称不能为空");
        }
        if (request.webhookUrl() == null || !request.webhookUrl().trim().matches("(?i)https?://.+")) {
            throw ApiException.badRequest("Webhook 必须是 http(s) URL");
        }
        normalizeType(request.channelType());
    }

    private String normalizeType(String value) {
        String type = value == null || value.isBlank() ? "GENERIC"
                : value.trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(type)) {
            throw ApiException.badRequest("机器人类型仅支持 GENERIC/DINGTALK/WECOM/FEISHU");
        }
        return type;
    }

    private String normalizeEvents(String events) {
        if (events == null || events.isBlank()) {
            return EVENT;
        }
        return events.trim().toUpperCase(Locale.ROOT);
    }

    private NotificationChannel require(String id) {
        return channels.findById(id).orElseThrow(() -> ApiException.notFound("通知机器人不存在: " + id));
    }

    private ChannelView view(NotificationChannel channel) {
        return new ChannelView(channel.getId(), channel.getName(), channel.getChannelType(),
                channel.getWebhookUrl(), channel.getSecret() != null && !channel.getSecret().isBlank(),
                channel.isEnabled(), channel.getEvents(), channel.getCreatedBy(), channel.getCreatedAt(),
                channel.getUpdatedAt(), channel.getLastTestAt(), channel.getLastTestStatus());
    }

    private String statusText(RunStatus status) {
        return switch (status) {
            case COMPLETED -> "成功";
            case PARTIAL -> "部分失败";
            case FAILED -> "失败";
            default -> "运行中";
        };
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
