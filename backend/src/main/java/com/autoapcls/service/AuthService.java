package com.autoapcls.service;

import com.autoapcls.mapper.*;
import com.autoapcls.model.entity.*;
import com.autoapcls.model.dto.*;
import com.autoapcls.security.JwtTokenProvider;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final UserEbsMappingMapper userEbsMappingMapper;
    private final UserSessionMapper userSessionMapper;
    private final EBSIntegrationService ebsService;

    // ──────────── 登录 ────────────

    public Map<String, Object> login(LoginRequest req) {
        if (req.getUsername() == null || req.getUsername().isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (req.getPassword() == null || req.getPassword().isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }

        // 根据 ebs_user_name 查找用户（同一 EBS 账号可能有多个飞书映射，取第一条激活的）
        List<UserEbsMapping> mappings = userEbsMappingMapper.selectList(
                new LambdaQueryWrapper<UserEbsMapping>()
                        .eq(UserEbsMapping::getEbsUserName, req.getUsername().trim().toUpperCase())
                        .eq(UserEbsMapping::getIsActive, true)
                        .last("LIMIT 1")
        );

        if (mappings.isEmpty()) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        UserEbsMapping mapping = mappings.get(0);

        // 验证密码（首次迁移后密码可能为空，拒绝空密码登录）
        if (mapping.getPassword() == null || mapping.getPassword().isEmpty()) {
            throw new IllegalArgumentException("账户未初始化密码，请联系管理员");
        }

        if (!passwordEncoder.matches(req.getPassword(), mapping.getPassword())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }

        // 生成 JWT（feishuOpenId 填充 ebsUserName 保证兼容）
        String token = jwtTokenProvider.generateToken(mapping.getEbsUserName(), mapping.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("feishuOpenId", mapping.getEbsUserName());
        result.put("feishuName", mapping.getFeishuName());
        result.put("ebsUserName", mapping.getEbsUserName());
        result.put("userId", mapping.getId());
        return result;
    }

    // ──────────── 用户信息 ────────────

    public Map<String, Object> getUserInfo(Long userId) {
        UserEbsMapping mapping = userEbsMappingMapper.selectById(userId);
        if (mapping == null) throw new IllegalArgumentException("用户不存在");
        Map<String, Object> info = new HashMap<>();
        info.put("userId", mapping.getId());
        info.put("feishuOpenId", mapping.getFeishuOpenId());
        info.put("feishuName", mapping.getFeishuName());
        info.put("ebsUserName", mapping.getEbsUserName());
        return info;
    }

    // ──────────── 职责列表（从 EBS 查询） ────────────

    /**
     * 从 EBS 查询当前用户可访问的职责列表
     */
    public List<Map<String, Object>> getResponsibilities(Long userId) {
        UserEbsMapping mapping = userEbsMappingMapper.selectById(userId);
        if (mapping == null) {
            log.warn("用户不存在: userId={}", userId);
            return getFallbackResponsibilities();
        }
        return getResponsibilitiesByEbsUser(mapping.getEbsUserName());
    }

    // 无参版本（兼容旧调用）
    public List<Map<String, Object>> getResponsibilities() {
        return getFallbackResponsibilities();
    }

    /**
     * 根据 EBS 用户名查询职责
     */
    public List<Map<String, Object>> getResponsibilitiesByEbsUser(String ebsUserName) {
        String sql = """
                SELECT fr.responsibility_id AS resp_id,
                       fr.responsibility_name AS resp_name,
                       fa.application_short_name AS app_name
                FROM apps.fnd_user_resp_groups frg
                JOIN apps.fnd_user fu ON frg.user_id = fu.user_id
                JOIN apps.fnd_responsibility_vl fr ON frg.responsibility_id = fr.responsibility_id
                JOIN apps.fnd_application_vl fa ON fr.application_id = fa.application_id
                WHERE fu.user_name = :ebsUserName
                  AND NVL(frg.end_date, SYSDATE) >= SYSDATE
                  AND NVL(fr.end_date, SYSDATE) >= SYSDATE
                ORDER BY fr.responsibility_name
                """;
        try {
            List<Map<String, Object>> result = ebsService.executeQuery(sql,
                    Map.of("ebsUserName", ebsUserName.toUpperCase()));
            log.info("[EBS] 查询到 {} 个职责: user={}", result.size(), ebsUserName);
            if (result.isEmpty()) {
                return getFallbackResponsibilities();
            }
            return normalizeKeys(result);
        } catch (Exception e) {
            log.error("[EBS] 查询职责失败: user={}, error={}", ebsUserName, e.getMessage(), e);
            return getFallbackResponsibilities();
        }
    }

    // ──────────── 库存组织列表（从 EBS 查询） ────────────

    /**
     * 从 EBS 查询所有可用库存组织
     */
    public List<Map<String, Object>> getOrganizations(Long userId) {
        return getOrganizationsByOu(null);
    }

    /**
     * 根据职责 ID 过滤库存组织（通过职责的 ORG_ID 配置文件获取 OU，再过滤该 OU 下的组织）
     */
    public List<Map<String, Object>> getOrganizationsByResp(Integer respId) {
        // 查询该职责的 ORG_ID 配置文件值
        String orgIdVal = getProfileOption("ORG_ID", respId);
        if (orgIdVal == null || orgIdVal.isEmpty()) {
            log.info("[EBS] 职责 {} 未设置 ORG_ID 配置文件，返回所有库存组织", respId);
            return getOrganizationsByOu(null);
        }
        try {
            int ouId = Integer.parseInt(orgIdVal);
            log.info("[EBS] 职责 {} → ORG_ID={}, 按 OU 过滤组织", respId, ouId);
            return getOrganizationsByOu(ouId);
        } catch (NumberFormatException e) {
            log.warn("[EBS] ORG_ID 值无法解析: {}", orgIdVal);
            return getOrganizationsByOu(null);
        }
    }

    /**
     * 按 OU 过滤库存组织（ouId 为 null 则不过滤）
     */
    private List<Map<String, Object>> getOrganizationsByOu(Integer ouId) {
        String sql;
        Map<String, Object> params;
        if (ouId != null) {
            sql = """
                    SELECT mp.organization_id   AS org_id,
                           mp.organization_code AS org_code,
                           haou.name            AS org_name
                    FROM apps.mtl_parameters mp
                    JOIN apps.hr_all_organization_units haou
                      ON mp.organization_id = haou.organization_id
                    WHERE EXISTS (
                        SELECT 1 FROM apps.org_organization_definitions ood
                        WHERE ood.organization_id = mp.organization_id
                          AND ood.operating_unit = :ouId
                    )
                    ORDER BY mp.organization_code
                    """;
            params = Map.of("ouId", ouId);
        } else {
            sql = """
                    SELECT mp.organization_id   AS org_id,
                           mp.organization_code AS org_code,
                           haou.name            AS org_name
                    FROM apps.mtl_parameters mp
                    JOIN apps.hr_all_organization_units haou
                      ON mp.organization_id = haou.organization_id
                    ORDER BY mp.organization_code
                    """;
            params = null;
        }
        try {
            List<Map<String, Object>> result = ebsService.executeQuery(sql, params);
            log.info("[EBS] 查询到 {} 个库存组织 (ouId={})", result.size(), ouId);
            if (result.isEmpty()) {
                return getFallbackOrganizations();
            }
            return normalizeKeys(result);
        } catch (Exception e) {
            log.error("[EBS] 查询库存组织失败: ouId={}, error={}", ouId, e.getMessage(), e);
            return getFallbackOrganizations();
        }
    }

    // 无参版本（兼容旧调用）
    public List<Map<String, Object>> getOrganizations() {
        return getFallbackOrganizations();
    }

    // ──────────── 会话选择（含 EBS 配置查询） ────────────

    /**
     * 保存用户会话选择，从 EBS 查询默认 OU 和账套
     */
    @Transactional
    public UserSession selectSession(Long userId, String feishuOpenId, SessionSelectRequest req) {
        UserSession session = new UserSession();
        session.setUserId(userId);
        session.setFeishuOpenId(feishuOpenId);
        session.setSelectedRespId(req.getRespId());
        session.setSelectedRespName(req.getRespName());
        session.setSelectedOrgId(req.getOrgId());
        session.setSelectedOrgCode(req.getOrgCode());

        // 从 EBS 查询 OU 配置（通过 ORG_ID 配置文件）
        fetchEDefaultOu(session);

        // 从 EBS 查询账套配置（通过 GL_SET_OF_BKS_ID 配置文件）
        fetchEDefaultLedger(session);

        // 如果 EBS 查询失败，使用兜底默认值
        if (session.getDefaultOuId() == null) {
            session.setDefaultOuId(201);
            session.setDefaultOuName("丽珠医药集团 (默认)");
        }
        if (session.getDefaultLedgerId() == null) {
            session.setDefaultLedgerId(301);
            session.setDefaultLedgerName("CNY 主账套 (默认)");
        }

        session.setPeriodName(req.getPeriodName() != null ? req.getPeriodName() : "2026-07");
        session.setLoginAt(LocalDateTime.now());
        session.setExpireAt(LocalDateTime.now().plusHours(8));
        userSessionMapper.insert(session);
        log.info("用户会话已创建: sessionId={}, orgCode={}, period={}, ou={}, ledger={}",
                session.getId(), session.getSelectedOrgCode(), session.getPeriodName(),
                session.getDefaultOuName(), session.getDefaultLedgerName());
        return session;
    }

    /**
     * 从 EBS 查询默认业务实体 (OU)
     * 优先查职责级别 -> 站点级别
     */
    private void fetchEDefaultOu(UserSession session) {
        try {
            String orgIdVal = getProfileOption("ORG_ID", session.getSelectedRespId());
            if (orgIdVal == null || orgIdVal.isEmpty()) {
                log.info("[EBS] ORG_ID 配置文件未设置，使用默认值");
                return;
            }
            int orgId = Integer.parseInt(orgIdVal);
            String sql = """
                    SELECT organization_id, name
                    FROM apps.hr_operating_units
                    WHERE organization_id = :orgId
                    """;
            List<Map<String, Object>> rows = ebsService.executeQuery(sql, Map.of("orgId", orgId));
            if (!rows.isEmpty()) {
                session.setDefaultOuId(orgId);
                session.setDefaultOuName(String.valueOf(rows.get(0).get("name")));
                log.info("[EBS] 默认 OU: id={}, name={}", orgId, session.getDefaultOuName());
            }
        } catch (Exception e) {
            log.warn("[EBS] 查询默认 OU 失败: {}", e.getMessage());
        }
    }

    /**
     * 从 EBS 查询默认账套
     */
    private void fetchEDefaultLedger(UserSession session) {
        try {
            String ledgerIdVal = getProfileOption("GL_SET_OF_BKS_ID", session.getSelectedRespId());
            if (ledgerIdVal == null || ledgerIdVal.isEmpty()) {
                log.info("[EBS] GL_SET_OF_BKS_ID 配置文件未设置，使用默认值");
                return;
            }
            int ledgerId = Integer.parseInt(ledgerIdVal);
            String sql = """
                    SELECT ledger_id, name
                    FROM apps.gl_ledgers
                    WHERE ledger_id = :ledgerId
                    """;
            List<Map<String, Object>> rows = ebsService.executeQuery(sql, Map.of("ledgerId", ledgerId));
            if (!rows.isEmpty()) {
                session.setDefaultLedgerId(ledgerId);
                session.setDefaultLedgerName(String.valueOf(rows.get(0).get("name")));
                log.info("[EBS] 默认账套: id={}, name={}", ledgerId, session.getDefaultLedgerName());
            }
        } catch (Exception e) {
            log.warn("[EBS] 查询默认账套失败: {}", e.getMessage());
        }
    }

    /**
     * 查询 EBS 配置文件值（职责级别优先，站点级别兜底）
     */
    private String getProfileOption(String profileName, Integer respId) {
        try {
            String sql = """
                    SELECT profile_option_value
                    FROM (
                        SELECT fpov.profile_option_value
                        FROM apps.fnd_profile_option_values fpov
                        JOIN apps.fnd_profile_options fpo ON fpov.profile_option_id = fpo.profile_option_id
                        WHERE fpo.profile_option_name = :profileName
                          AND ((fpov.level_id = 10003 AND fpov.level_value = :respIdStr)
                            OR (fpov.level_id = 10001 AND fpov.level_value = '0'))
                        ORDER BY CASE WHEN fpov.level_id = 10003 THEN 1 ELSE 2 END
                    ) WHERE ROWNUM = 1
                    """;
            List<Map<String, Object>> rows = ebsService.executeQuery(sql,
                    Map.of("profileName", profileName, "respIdStr", String.valueOf(respId)));
            if (!rows.isEmpty() && rows.get(0).get("profile_option_value") != null) {
                return rows.get(0).get("profile_option_value").toString();
            }
        } catch (Exception e) {
            log.warn("[EBS] 查询配置文件 {} 失败: {}", profileName, e.getMessage());
        }
        return null;
    }

    // ──────────── 兜底 Mock 数据 ────────────

    // ──────────── Oracle 键名转换 ────────────

    /**
     * 将 Oracle JDBC 返回的 UPPER_SNAKE_CASE 键名转为 camelCase
     */
    private List<Map<String, Object>> normalizeKeys(List<Map<String, Object>> rows) {
        return rows.stream()
                .map(row -> {
                    Map<String, Object> normalized = new LinkedHashMap<>();
                    row.forEach((key, value) -> normalized.put(toCamelCase(key), value));
                    return normalized;
                })
                .collect(Collectors.toList());
    }

    private String toCamelCase(String s) {
        StringBuilder sb = new StringBuilder();
        boolean upper = false;
        for (char c : s.toLowerCase().toCharArray()) {
            if (c == '_') {
                upper = true;
                continue;
            }
            sb.append(upper ? Character.toUpperCase(c) : c);
            upper = false;
        }
        return sb.toString();
    }

    // ──────────── 兜底 Mock 数据 ────────────

    private List<Map<String, Object>> getFallbackResponsibilities() {
        log.warn("[EBS] 无法查询职责，返回兜底数据");
        return List.of(
            Map.of("respId", 51001, "respName", "应付会计", "appName", "SQLAP"),
            Map.of("respId", 51002, "respName", "总账会计", "appName", "SQLGL"),
            Map.of("respId", 51003, "respName", "成本会计", "appName", "BOM")
        );
    }

    private List<Map<String, Object>> getFallbackOrganizations() {
        log.warn("[EBS] 无法查询库存组织，返回兜底数据");
        return List.of(
            Map.of("orgId", 101, "orgCode", "ORG001", "orgName", "丽珠制药厂"),
            Map.of("orgId", 102, "orgCode", "ORG002", "orgName", "丽珠合成厂"),
            Map.of("orgId", 103, "orgCode", "ORG003", "orgName", "丽珠试剂厂")
        );
    }

    // ──────────── 活跃会话 ────────────

    public UserSession getActiveSession(Long userId) {
        return userSessionMapper.selectOne(
                new LambdaQueryWrapper<UserSession>()
                        .eq(UserSession::getUserId, userId)
                        .orderByDesc(UserSession::getLoginAt)
                        .last("LIMIT 1")
        );
    }
}
