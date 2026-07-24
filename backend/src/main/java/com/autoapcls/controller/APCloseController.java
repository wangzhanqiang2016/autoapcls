package com.autoapcls.controller;

import com.autoapcls.common.Result;
import com.autoapcls.model.dto.StepExecuteRequest;
import com.autoapcls.model.entity.UserSession;
import com.autoapcls.service.APCloseService;
import com.autoapcls.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ap-close")
@RequiredArgsConstructor
public class APCloseController {

    private final APCloseService apCloseService;
    private final AuthService authService;

    // 获取当前活跃会话
    private UserSession getCurrentSession(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) throw new IllegalArgumentException("未登录");
        UserSession session = authService.getActiveSession(userId);
        if (session == null) throw new IllegalArgumentException("未选择职责和组织，请先完成会话配置");
        return session;
    }

    // 获取可用期间列表
    @GetMapping("/periods")
    public Result<?> getPeriods() {
        return Result.ok(apCloseService.getAvailablePeriods());
    }

    // 初始化月结任务
    @PostMapping("/init")
    public Result<?> initTasks(HttpServletRequest request,
                                @RequestParam(required = false) String periodName) {
        UserSession session = getCurrentSession(request);
        if (periodName == null) periodName = session.getPeriodName();
        return Result.ok(apCloseService.initTasks(session.getId(), periodName));
    }

    // 获取所有步骤状态
    @GetMapping("/tasks")
    public Result<?> getTasks(HttpServletRequest request) {
        UserSession session = getCurrentSession(request);
        return Result.ok(apCloseService.getTasks(session.getId()));
    }

    // 获取单个步骤详情
    @GetMapping("/tasks/{stepNo}")
    public Result<?> getTaskDetail(HttpServletRequest request, @PathVariable Integer stepNo) {
        UserSession session = getCurrentSession(request);
        return Result.ok(apCloseService.getTaskDetail(session.getId(), stepNo));
    }

    // 执行步骤
    @PostMapping("/tasks/{stepNo}/execute")
    public Result<?> executeStep(HttpServletRequest request,
                                  @PathVariable Integer stepNo,
                                  @RequestBody(required = false) StepExecuteRequest req) {
        UserSession session = getCurrentSession(request);
        if (req == null) req = new StepExecuteRequest();
        return Result.ok(apCloseService.executeStep(session.getId(), stepNo, req));
    }

    // 人工确认步骤完成
    @PostMapping("/tasks/{stepNo}/confirm")
    public Result<?> confirmStep(HttpServletRequest request, @PathVariable Integer stepNo) {
        UserSession session = getCurrentSession(request);
        return Result.ok(apCloseService.confirmStep(session.getId(), stepNo));
    }

    // 轮询步骤执行状态
    @GetMapping("/tasks/{stepNo}/status")
    public Result<?> getStepStatus(HttpServletRequest request, @PathVariable Integer stepNo) {
        UserSession session = getCurrentSession(request);
        return Result.ok(apCloseService.getStepStatus(session.getId(), stepNo));
    }
}
