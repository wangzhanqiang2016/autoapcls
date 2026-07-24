package com.autoapcls.controller;

import com.autoapcls.common.Result;
import com.autoapcls.model.dto.LoginRequest;
import com.autoapcls.model.dto.SessionSelectRequest;
import com.autoapcls.model.entity.UserSession;
import com.autoapcls.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // 账户密码登录
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest req) {
        Map<String, Object> loginResult = authService.login(req);
        return Result.ok(loginResult);
    }

    // 获取当前用户信息
    @GetMapping("/user-info")
    public Result<Map<String, Object>> getUserInfo(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) return Result.error(401, "未登录");
        return Result.ok(authService.getUserInfo(userId));
    }

    // 获取职责列表
    @GetMapping("/responsibilities")
    public Result<?> getResponsibilities(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(authService.getResponsibilities(userId));
    }

    // 获取库存组织列表（可选按职责过滤）
    @GetMapping("/organizations")
    public Result<?> getOrganizations(HttpServletRequest request,
                                      @RequestParam(required = false) Integer respId) {
        Long userId = (Long) request.getAttribute("userId");
        if (respId != null) {
            return Result.ok(authService.getOrganizationsByResp(respId));
        }
        return Result.ok(authService.getOrganizations(userId));
    }

    // 保存用户选择的职责和组织
    @PostMapping("/select-session")
    public Result<UserSession> selectSession(HttpServletRequest request,
                                              @RequestBody SessionSelectRequest req) {
        Long userId = (Long) request.getAttribute("userId");
        String feishuOpenId = (String) request.getAttribute("feishuOpenId");
        if (userId == null) return Result.error(401, "未登录");
        UserSession session = authService.selectSession(userId, feishuOpenId, req);
        return Result.ok(session);
    }

    // 登出
    @PostMapping("/logout")
    public Result<?> logout() {
        return Result.ok(Map.of("message", "已登出"));
    }
}
