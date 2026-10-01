package com.quality.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.common.base.UserThreadLocal;
import com.common.models.SaasUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * 调优闭环下游调用：System B（analyze / prompts）、chat-server（agent meta）、recall（meta_sync）。
 * 用 JDK HttpClient，避免引入新依赖；透传当前用户 token + tenantid。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DownstreamClient {

    private final QualityProperties props;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    // ------------------------------------------------------------------ System B

    /** 调 System B analyze；tuningTaskId 非空时带 X-Tuning-Task 头（跳过 recall）。 */
    public JSONObject analyze(String requestId, String query, JSONObject databaseMeta,
                              Map<String, String> promptOverrides, Long tuningTaskId, int timeoutSeconds) {
        JSONObject body = new JSONObject();
        body.put("request_id", requestId);
        body.put("query", query);
        body.put("database_meta", databaseMeta);
        body.put("skip_chat_clear", true);
        if (promptOverrides != null && !promptOverrides.isEmpty()) {
            body.put("prompt_overrides", promptOverrides);
        }
        HttpRequest.Builder b = base(props.getSystemBUrl() + "/api/v1/analyze", timeoutSeconds)
                .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString(), StandardCharsets.UTF_8));
        if (tuningTaskId != null) {
            b.header("X-Tuning-Task", String.valueOf(tuningTaskId));
        }
        return send(b.build());
    }

    public JSONObject systemBPost(String path, JSONObject body) {
        HttpRequest req = base(props.getSystemBUrl() + path, 30)
                .POST(HttpRequest.BodyPublishers.ofString(body == null ? "{}" : body.toJSONString(), StandardCharsets.UTF_8))
                .build();
        return send(req);
    }

    public JSONObject promptCurrent(String groupName) {
        JSONObject groups = systemBPost("/api/v1/prompts/groups", new JSONObject());
        Integer groupId = findGroupId(groups, groupName);
        if (groupId == null) return null;
        JSONObject req = new JSONObject();
        req.put("group_id", groupId);
        JSONObject r = systemBPost("/api/v1/prompts/current", req);
        JSONObject data = r == null ? null : r.getJSONObject("data");
        if (data != null) data.put("group_id", groupId);
        return data;
    }

    public JSONObject promptSwitch(String groupName, String version) {
        JSONObject groups = systemBPost("/api/v1/prompts/groups", new JSONObject());
        Integer groupId = findGroupId(groups, groupName);
        if (groupId == null) throw new IllegalStateException("提示词分组不存在: " + groupName);
        JSONObject req = new JSONObject();
        req.put("group_id", groupId);
        req.put("version", version);
        return systemBPost("/api/v1/prompts/switch", req);
    }

    public JSONObject systemBLogByRequest(String requestId) {
        JSONObject req = new JSONObject();
        req.put("request_id", requestId);
        return systemBPost("/api/v1/logs/by-request", req);
    }

    private Integer findGroupId(JSONObject groups, String name) {
        if (groups == null) return null;
        Object data = groups.get("data");
        com.alibaba.fastjson.JSONArray arr = null;
        if (data instanceof com.alibaba.fastjson.JSONArray) arr = (com.alibaba.fastjson.JSONArray) data;
        else if (data instanceof JSONObject) arr = ((JSONObject) data).getJSONArray("list");
        if (arr == null) return null;
        for (int i = 0; i < arr.size(); i++) {
            JSONObject g = arr.getJSONObject(i);
            if (name.equals(g.getString("name"))) return g.getInteger("id");
        }
        return null;
    }

    // --------------------------------------------------------------- chat-server

    /** 智能体线上 database_meta（与 biChat 传给 System B 的一致） */
    public JSONObject agentMeta(String aiBodyCode) {
        String url = props.getChatServerUrl() + "/api/chat-server/metadata/agent?code="
                + URLEncoder.encode(aiBodyCode, StandardCharsets.UTF_8);
        JSONObject r = send(base(url, 30).GET().build());
        if (r == null) return null;
        return r.containsKey("data") ? r.getJSONObject("data") : r;
    }

    // -------------------------------------------------------------------- recall

    public JSONObject recallMetaSync() {
        try {
            HttpRequest req = base(props.getRecallUrl() + "/api/metadata/sync", 60)
                    .POST(HttpRequest.BodyPublishers.ofString("{}", StandardCharsets.UTF_8)).build();
            return send(req);
        } catch (Exception e) {
            log.warn("[quality] recall meta_sync failed (non-fatal): {}", e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------- helpers

    private HttpRequest.Builder base(String url, int timeoutSeconds) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json");
        SaasUser u = UserThreadLocal.get();
        if (u != null) {
            if (StringUtils.isNotBlank(u.getToken())) {
                String token = u.getToken().startsWith("Bearer ") ? u.getToken() : "Bearer " + u.getToken();
                b.header("Authorization", token);
            }
            if (u.getTenantId() != null) b.header("tenantid", String.valueOf(u.getTenantId()));
        }
        return b;
    }

    private JSONObject send(HttpRequest req) {
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String body = resp.body();
            if (StringUtils.isBlank(body)) {
                JSONObject o = new JSONObject();
                o.put("code", resp.statusCode());
                return o;
            }
            JSONObject json = JSON.parseObject(body);
            if (resp.statusCode() >= 400 && json != null && !json.containsKey("status")) {
                json.put("http_status", resp.statusCode());
            }
            return json;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("下游调用失败 " + req.uri() + ": " + e.getMessage(), e);
        }
    }
}
