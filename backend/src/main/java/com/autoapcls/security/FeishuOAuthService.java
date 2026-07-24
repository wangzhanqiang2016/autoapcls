package com.autoapcls.security;

import com.autoapcls.mapper.UserEbsMappingMapper;
import com.autoapcls.model.entity.UserEbsMapping;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeishuOAuthService {

    private final UserEbsMappingMapper userEbsMappingMapper;
    private final RestTemplateBuilder restTemplateBuilder;

    @Value("${feishu.mock-enabled:true}")
    private boolean mockEnabled;

    @Value("${feishu.app-id}")
    private String appId;

    @Value("${feishu.app-secret}")
    private String appSecret;

    @Value("${feishu.redirect-uri}")
    private String redirectUri;

    // App Access Token 缓存
    private volatile String cachedAppAccessToken;
    private volatile long tokenExpireAt = 0;

    // OAuth state 缓存（防 CSRF）
    private volatile String currentState;
    private volatile long stateExpireAt = 0;

    /**
     * 获取飞书 OAuth 授权 URL（使用默认配置的 redirect_uri）
     */
    public String getAuthorizationUrl() {
        return buildAuthorizationUrl(redirectUri);
    }

    /**
     * 获取飞书 OAuth 授权 URL（使用前端传入的 redirect_uri，用于局域网访问等场景）
     * @param externalRedirectUri 前端传入的回调地址，须以 /login/callback 结尾且端口为 3001
     */
    public String getAuthorizationUrl(String externalRedirectUri) {
        // 安全校验：必须以 /login/callback 结尾，且是 http/https
        if (externalRedirectUri == null || externalRedirectUri.isEmpty()) {
            return buildAuthorizationUrl(redirectUri);
        }
        if (!externalRedirectUri.matches("^https?://[^/]+:3001/login/callback$")) {
            log.warn("[飞书OAuth] 非法 redirect_uri 被拒绝: {}", externalRedirectUri);
            return buildAuthorizationUrl(redirectUri);
        }
        return buildAuthorizationUrl(externalRedirectUri);
    }

    private String buildAuthorizationUrl(String uri) {
        String state = UUID.randomUUID().toString().replace("-", "");
        currentState = state;
        stateExpireAt = System.currentTimeMillis() + 600_000; // 10 分钟有效
        return "https://open.feishu.cn/open-apis/authen/v1/authorize"
                + "?app_id=" + appId
                + "&redirect_uri=" + URLEncoder.encode(uri, StandardCharsets.UTF_8)
                + "&state=" + state;
    }

    /**
     * 验证并消费 state 参数（防 CSRF，一次性使用）
     */
    public boolean validateState(String state) {
        if (currentState == null || state == null) return false;
        if (currentState.equals(state) && System.currentTimeMillis() < stateExpireAt) {
            currentState = null; // 一次性消费，防重放
            return true;
        }
        return false;
    }

    /**
     * 通过授权码获取飞书用户信息，并查找对应的 EBS 账号映射
     */
    public UserEbsMapping authenticate(String code) {
        String feishuOpenId;
        String feishuName;

        if (mockEnabled) {
            log.info("[Mock] 飞书认证: code={}", code);
            feishuOpenId = code != null && !code.isEmpty() ? code : "ou_test_admin_001";
            feishuName = "测试会计";
        } else {
            Map<String, Object> userInfo = fetchFeishuUserInfo(code);
            feishuOpenId = (String) userInfo.get("open_id");
            feishuName = (String) userInfo.get("name");
            log.info("[飞书OAuth] 用户认证成功: openId={}, name={}", feishuOpenId, feishuName);
        }

        // 查找 EBS 账号映射
        UserEbsMapping mapping = userEbsMappingMapper.selectOne(
                new LambdaQueryWrapper<UserEbsMapping>()
                        .eq(UserEbsMapping::getFeishuOpenId, feishuOpenId)
                        .eq(UserEbsMapping::getIsActive, true)
        );

        if (mapping == null) {
            throw new IllegalArgumentException(
                    "未找到飞书用户对应的 EBS 账号映射，请联系管理员配置。Open ID: " + feishuOpenId);
        }

        return mapping;
    }

    // ═══════════ 飞书 OAuth API 调用 ═══════════

    /**
     * 用授权码换取飞书用户信息（OIDC 流程）
     * Step 1: 获取 app_access_token
     * Step 2: 用 app_access_token + code 换取用户 open_id 和 name
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchFeishuUserInfo(String code) {
        RestTemplate rt = restTemplateBuilder.build();

        // Step 1: 获取 app_access_token
        String appAccessToken = getAppAccessToken(rt);

        // Step 2: 用授权码换取用户令牌（OIDC 流程）
        String url = "https://open.feishu.cn/open-apis/authen/v1/oidc/access_token";

        Map<String, String> body = Map.of(
                "grant_type", "authorization_code",
                "code", code
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(appAccessToken);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = rt.postForEntity(url, request, Map.class);
            Map<String, Object> respBody = response.getBody();

            if (respBody == null || !"0".equals(String.valueOf(respBody.get("code")))) {
                // OIDC 端点返回 message（非 msg），兼容两种字段名
                String errMsg = respBody != null
                        ? String.valueOf(respBody.getOrDefault("message", respBody.getOrDefault("msg", "unknown")))
                        : "无响应";
                log.error("[飞书OAuth] 换取用户令牌失败: code={}, errMsg={}", code, errMsg);
                throw new RuntimeException("飞书授权失败: " + errMsg);
            }

            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            if (data == null) {
                throw new RuntimeException("飞书授权返回数据为空");
            }

            // OIDC 响应可能直接包含 open_id/name，也可能只有 access_token 需进一步查询
            String feishuOpenId = (String) data.get("open_id");
            String feishuName = (String) data.get("name");

            if (feishuOpenId == null || feishuOpenId.isEmpty()) {
                // OIDC token 响应未直接返回用户信息，通过 user_access_token 调用 user_info 接口获取
                String userAccessToken = (String) data.get("access_token");
                if (userAccessToken == null || userAccessToken.isEmpty()) {
                    throw new RuntimeException("飞书授权返回缺少 access_token，无法获取用户信息");
                }
                log.info("[飞书OAuth] OIDC 响应不含 open_id，通过 user_info 接口获取");
                Map<String, Object> userInfo = fetchUserInfo(rt, userAccessToken);
                feishuOpenId = (String) userInfo.get("open_id");
                feishuName = (String) userInfo.getOrDefault("name", "未知用户");
            }

            return Map.of(
                    "open_id", feishuOpenId != null ? feishuOpenId : "",
                    "name", feishuName != null ? feishuName : "未知用户"
            );
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("[飞书OAuth] API 调用异常: {}", e.getMessage(), e);
            throw new RuntimeException("飞书 OAuth 认证失败: " + e.getMessage(), e);
        }
    }

    /**
     * 通过 user_access_token 调用飞书 user_info 接口获取用户 open_id 和 name
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchUserInfo(RestTemplate rt, String userAccessToken) {
        String url = "https://open.feishu.cn/open-apis/authen/v1/user_info";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(userAccessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = rt.exchange(url, HttpMethod.GET, request, Map.class);
            Map<String, Object> respBody = response.getBody();

            if (respBody == null || !"0".equals(String.valueOf(respBody.get("code")))) {
                String msg = respBody != null ? String.valueOf(respBody.get("msg")) : "无响应";
                log.error("[飞书OAuth] 获取用户信息失败: msg={}", msg);
                throw new RuntimeException("获取飞书用户信息失败: " + msg);
            }

            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            if (data == null) {
                throw new RuntimeException("飞书用户信息返回数据为空");
            }

            log.info("[飞书OAuth] user_info 响应: open_id={}, name={}, en_name={}",
                    data.get("open_id"), data.get("name"), data.get("en_name"));

            return Map.of(
                    "open_id", data.getOrDefault("open_id", ""),
                    "name", data.getOrDefault("name",
                            data.getOrDefault("en_name", "未知用户"))
            );
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("[飞书OAuth] 获取用户信息异常: {}", e.getMessage(), e);
            throw new RuntimeException("获取飞书用户信息失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取飞书 app_access_token（带缓存）
     */
    @SuppressWarnings("unchecked")
    private synchronized String getAppAccessToken(RestTemplate rt) {
        // 缓存未过期
        if (cachedAppAccessToken != null && System.currentTimeMillis() < tokenExpireAt) {
            return cachedAppAccessToken;
        }

        String url = "https://open.feishu.cn/open-apis/auth/v3/app_access_token/internal";

        Map<String, String> body = Map.of(
                "app_id", appId,
                "app_secret", appSecret
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = rt.postForEntity(url, request, Map.class);
            Map<String, Object> respBody = response.getBody();

            if (respBody == null || !"0".equals(String.valueOf(respBody.get("code")))) {
                String msg = respBody != null ? String.valueOf(respBody.get("msg")) : "无响应";
                throw new RuntimeException("获取飞书 app_access_token 失败: " + msg);
            }

            cachedAppAccessToken = (String) respBody.get("app_access_token");
            // 飞书 token 有效期 2 小时，提前 5 分钟过期
            int expire = 7200;
            if (respBody.get("expire") instanceof Number) {
                expire = ((Number) respBody.get("expire")).intValue();
            }
            tokenExpireAt = System.currentTimeMillis() + (expire - 300) * 1000L;
            log.info("[飞书OAuth] app_access_token 已刷新, 有效期 {}s", expire);

            return cachedAppAccessToken;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("[飞书OAuth] 获取 app_access_token 异常: {}", e.getMessage(), e);
            throw new RuntimeException("获取飞书 app_access_token 失败: " + e.getMessage(), e);
        }
    }
}
