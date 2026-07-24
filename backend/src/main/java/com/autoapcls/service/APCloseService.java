package com.autoapcls.service;

import com.autoapcls.mapper.ApCloseStepDefMapper;
import com.autoapcls.mapper.ApCloseTaskMapper;
import com.autoapcls.mapper.UserSessionMapper;
import com.autoapcls.model.dto.StepExecuteRequest;
import com.autoapcls.model.entity.ApCloseStepDef;
import com.autoapcls.model.entity.ApCloseTask;
import com.autoapcls.model.entity.UserSession;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class APCloseService {

    private final ApCloseStepDefMapper stepDefMapper;
    private final ApCloseTaskMapper taskMapper;
    private final UserSessionMapper sessionMapper;
    private final EBSIntegrationService ebsService;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 获取所有步骤定义（按 stepNo 排序）
    public List<ApCloseStepDef> getStepDefs() {
        return stepDefMapper.selectList(
                new LambdaQueryWrapper<ApCloseStepDef>()
                        .orderByAsc(ApCloseStepDef::getStepNo)
        );
    }

    // 获取可用期间列表 (Mock)
    public List<String> getAvailablePeriods() {
        return List.of("2026-01", "2026-02", "2026-03", "2026-04", "2026-05", "2026-06",
                "2026-07", "2026-08", "2026-09", "2026-10", "2026-11", "2026-12");
    }

    // 初始化月结任务：为当前会话创建 14 条 step 记录
    @Transactional
    public List<Map<String, Object>> initTasks(Long sessionId, String periodName) {
        UserSession session = sessionMapper.selectById(sessionId);
        if (session == null) throw new IllegalArgumentException("会话不存在");

        // 检查是否已初始化
        long existing = taskMapper.selectCount(
                new LambdaQueryWrapper<ApCloseTask>()
                        .eq(ApCloseTask::getSessionId, sessionId)
                        .eq(ApCloseTask::getPeriodName, periodName)
        );
        if (existing > 0) {
            log.info("月结任务已存在: sessionId={}, period={}", sessionId, periodName);
            return getTasks(sessionId);
        }

        // 创建 14 步任务
        List<ApCloseStepDef> stepDefs = getStepDefs();
        List<ApCloseTask> tasks = new ArrayList<>();
        for (ApCloseStepDef def : stepDefs) {
            ApCloseTask task = new ApCloseTask();
            task.setSessionId(sessionId);
            task.setStepNo(def.getStepNo());
            task.setPeriodName(periodName);
            task.setOrgId(session.getSelectedOrgId());
            task.setOuId(session.getDefaultOuId());
            task.setLedgerId(session.getDefaultLedgerId());
            task.setStatus("PENDING");
            taskMapper.insert(task);
            tasks.add(task);
        }
        log.info("月结任务已初始化: sessionId={}, period={}, 共 {} 步", sessionId, periodName, tasks.size());
        return getTasks(sessionId);
    }

    // 获取当前会话的所有任务状态（含步骤定义信息）
    public List<Map<String, Object>> getTasks(Long sessionId) {
        List<ApCloseTask> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<ApCloseTask>()
                        .eq(ApCloseTask::getSessionId, sessionId)
                        .orderByAsc(ApCloseTask::getStepNo)
        );
        List<ApCloseStepDef> defs = getStepDefs();
        Map<Integer, ApCloseStepDef> defMap = defs.stream()
                .collect(Collectors.toMap(ApCloseStepDef::getStepNo, d -> d));

        return tasks.stream().map(task -> {
            Map<String, Object> m = new HashMap<>();
            m.put("taskId", task.getId());
            m.put("stepNo", task.getStepNo());
            m.put("stepName", defMap.containsKey(task.getStepNo())
                    ? defMap.get(task.getStepNo()).getStepName() : "");
            m.put("stepType", defMap.containsKey(task.getStepNo())
                    ? defMap.get(task.getStepNo()).getStepType() : "");
            m.put("description", defMap.containsKey(task.getStepNo())
                    ? defMap.get(task.getStepNo()).getDescription() : "");
            m.put("status", task.getStatus());
            m.put("ebsRequestId", task.getEbsRequestId());
            m.put("ebsRequestStatus", task.getEbsRequestStatus());
            m.put("outputFilePath", task.getOutputFilePath());
            m.put("errorMessage", task.getErrorMessage());
            m.put("startedAt", task.getStartedAt());
            m.put("completedAt", task.getCompletedAt());
            return m;
        }).collect(Collectors.toList());
    }

    // 获取单个步骤详情
    public Map<String, Object> getTaskDetail(Long sessionId, Integer stepNo) {
        ApCloseTask task = taskMapper.selectOne(
                new LambdaQueryWrapper<ApCloseTask>()
                        .eq(ApCloseTask::getSessionId, sessionId)
                        .eq(ApCloseTask::getStepNo, stepNo)
        );
        if (task == null) throw new IllegalArgumentException("步骤不存在: stepNo=" + stepNo);

        ApCloseStepDef def = stepDefMapper.selectOne(
                new LambdaQueryWrapper<ApCloseStepDef>()
                        .eq(ApCloseStepDef::getStepNo, stepNo)
        );

        Map<String, Object> detail = new HashMap<>();
        detail.put("taskId", task.getId());
        detail.put("stepNo", task.getStepNo());
        detail.put("stepName", def != null ? def.getStepName() : "");
        detail.put("stepType", def != null ? def.getStepType() : "");
        detail.put("description", def != null ? def.getDescription() : "");
        detail.put("ebsProgram", def != null ? def.getEbsProgram() : "");
        detail.put("status", task.getStatus());
        detail.put("ebsRequestId", task.getEbsRequestId());
        detail.put("ebsRequestStatus", task.getEbsRequestStatus());
        detail.put("outputFilePath", task.getOutputFilePath());
        detail.put("errorMessage", task.getErrorMessage());
        detail.put("paramsJson", task.getParamsJson());
        detail.put("startedAt", task.getStartedAt());
        detail.put("completedAt", task.getCompletedAt());

        // 根据步骤类型构建默认参数
        Map<String, Object> defaultParams = buildDefaultParams(task, def);
        detail.put("defaultParams", defaultParams);

        // 对于 AUTO_CHECK，返回检查结果摘要
        if ("AUTO_CHECK".equals(def != null ? def.getStepType() : "") && "COMPLETED".equals(task.getStatus())) {
            detail.put("checkResult", Map.of(
                    "totalCount", 0,
                    "issues", Collections.emptyList()
            ));
        }

        return detail;
    }

    // 执行步骤
    @Transactional
    public Map<String, Object> executeStep(Long sessionId, Integer stepNo, StepExecuteRequest req) {
        ApCloseTask task = taskMapper.selectOne(
                new LambdaQueryWrapper<ApCloseTask>()
                        .eq(ApCloseTask::getSessionId, sessionId)
                        .eq(ApCloseTask::getStepNo, stepNo)
        );
        if (task == null) throw new IllegalArgumentException("步骤不存在");

        ApCloseStepDef def = stepDefMapper.selectOne(
                new LambdaQueryWrapper<ApCloseStepDef>()
                        .eq(ApCloseStepDef::getStepNo, stepNo)
        );

        task.setStatus("RUNNING");
        task.setStartedAt(LocalDateTime.now());
        Map<String, Object> params = req.getParams() != null ? req.getParams() : new HashMap<>();
        task.setParamsJson(params.toString());
        taskMapper.updateById(task);

        String stepType = def != null ? def.getStepType() : "";
        Map<String, Object> result = new HashMap<>();
        result.put("stepNo", stepNo);
        result.put("stepType", stepType);

        try {
            switch (stepType) {
                case "MANUAL_CONFIRM":
                    // 人工确认类型不在这里处理，由 confirmStep 处理
                    result.put("message", "请确认已完成此步骤");
                    break;

                case "AUTO_CHECK":
                    // 调用 CUX_AP_CLOSE_UTIL 包函数进行 EBS 数据检查
                    result.putAll(executeAutoCheck(task, def));
                    break;

                case "REPORT_EXPORT":
                    // Mock: 提交并发请求 → 轮询 → 获取输出文件
                    if (def != null && def.getEbsProgram() != null) {
                        Long requestId = ebsService.submitConcurrentRequest(def.getEbsProgram(), params);
                        task.setEbsRequestId(requestId);
                        // Mock 直接完成
                        Map<String, String> status = ebsService.getRequestStatus(requestId);
                        task.setEbsRequestStatus(status.get("statusCode"));
                        // Mock 保存文件
                        byte[] output = ebsService.getRequestOutput(requestId);
                        String fileName = def.getStepName() + "_" + task.getPeriodName() + ".xlsx";
                        UserSession session = sessionMapper.selectById(sessionId);
                        String orgCode = session != null ? session.getSelectedOrgCode() : "UNKNOWN";
                        fileStorageService.saveFile(orgCode, task.getPeriodName(), stepNo,
                                task.getId(), requestId, fileName, output, "XLSX");
                        task.setOutputFilePath(fileStorageService.getClass().getSimpleName()); // placeholder
                        result.put("requestId", requestId);
                        result.put("outputFile", fileName);
                    }
                    task.setStatus("COMPLETED");
                    task.setCompletedAt(LocalDateTime.now());
                    result.put("message", "报表已生成");
                    break;

                case "EBS_REQUEST":
                    // Mock: 提交并发请求
                    if (def != null && def.getEbsProgram() != null) {
                        Long requestId = ebsService.submitConcurrentRequest(def.getEbsProgram(), params);
                        task.setEbsRequestId(requestId);
                        Map<String, String> status = ebsService.getRequestStatus(requestId);
                        task.setEbsRequestStatus(status.get("statusCode"));
                        result.put("requestId", requestId);
                        result.put("status", status.get("statusCode"));
                    }
                    task.setStatus("COMPLETED");
                    task.setCompletedAt(LocalDateTime.now());
                    result.put("message", "请求已提交并完成");
                    break;

                default:
                    result.put("message", "未知步骤类型");
            }
        } catch (Exception e) {
            log.error("步骤执行失败: stepNo={}", stepNo, e);
            task.setStatus("FAILED");
            task.setErrorMessage(e.getMessage());
            result.put("message", "执行失败: " + e.getMessage());
        }

        taskMapper.updateById(task);

        result.put("status", task.getStatus());
        result.put("errorMessage", task.getErrorMessage());
        result.put("ebsRequestId", task.getEbsRequestId());
        return result;
    }

    // 人工确认步骤完成
    @Transactional
    public Map<String, Object> confirmStep(Long sessionId, Integer stepNo) {
        ApCloseTask task = taskMapper.selectOne(
                new LambdaQueryWrapper<ApCloseTask>()
                        .eq(ApCloseTask::getSessionId, sessionId)
                        .eq(ApCloseTask::getStepNo, stepNo)
        );
        if (task == null) throw new IllegalArgumentException("步骤不存在");

        task.setStatus("COMPLETED");
        task.setCompletedAt(LocalDateTime.now());
        taskMapper.updateById(task);

        Map<String, Object> result = new HashMap<>();
        result.put("stepNo", stepNo);
        result.put("status", "COMPLETED");
        result.put("message", "步骤已完成");
        return result;
    }

    // 获取步骤的执行状态（轮询）
    public Map<String, Object> getStepStatus(Long sessionId, Integer stepNo) {
        ApCloseTask task = taskMapper.selectOne(
                new LambdaQueryWrapper<ApCloseTask>()
                        .eq(ApCloseTask::getSessionId, sessionId)
                        .eq(ApCloseTask::getStepNo, stepNo)
        );
        if (task == null) throw new IllegalArgumentException("步骤不存在");

        Map<String, Object> status = new HashMap<>();
        status.put("stepNo", stepNo);
        status.put("status", task.getStatus());
        status.put("ebsRequestId", task.getEbsRequestId());
        status.put("ebsRequestStatus", task.getEbsRequestStatus());
        status.put("errorMessage", task.getErrorMessage());
        return status;
    }

    // 构建步骤默认参数
    private Map<String, Object> buildDefaultParams(ApCloseTask task, ApCloseStepDef def) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (def == null) return params;

        switch (def.getStepNo()) {
            case 4: // 应付发票信息报表
                params.put("起始GL日期", task.getPeriodName() + "-01");
                params.put("终止GL日期", getPeriodEndDate(task.getPeriodName()));
                params.put("包含已取消发票", "N");
                break;
            case 5: // 应付票据报表
                params.put("业务实体", task.getOuId());
                params.put("票据状态", "ISSUED");
                params.put("是否显示现金流", "N");
                break;
            case 6: // 更新到期应付票据状态
                params.put("到期日", "请选择日期");
                break;
            case 7: // 未入帐事务处理报表
                params.put("申报级别", "分类帐");
                params.put("申请环境", "CNY 子分类账");
                params.put("期间名称", task.getPeriodName());
                break;
            case 8: // 创建会计科目
            case 13:
                params.put("分类帐", "CNY 主账套");
                params.put("终止日期", getPeriodEndDate(task.getPeriodName()));
                params.put("模式", "FINAL");
                params.put("传送至GL", "Y");
                params.put("在GL中过帐", "Y");
                break;
            case 11: // 应付暂估汇总表
                params.put("起始日期", "2019-01");
                params.put("结束日期", task.getPeriodName());
                params.put("输出方式", "D");
                params.put("发票匹配截止日期", "请选择日期");
                params.put("是否显示匹配完的数据", "Y");
                break;
            case 12: // 供应商帐龄报表
                params.put("业务实体", task.getOuId());
                params.put("截止日期", getPeriodEndDate(task.getPeriodName()));
                params.put("应付帐龄期间", "丽珠应付账龄时段");
                params.put("帐龄类型", "ALL");
                params.put("明细汇总", "DETAIL");
                break;
            default:
                break;
        }
        return params;
    }

    // AUTO_CHECK 步骤：调用 CUX_AP_CLOSE_UTIL 检查 + 明细函数
    private Map<String, Object> executeAutoCheck(ApCloseTask task, ApCloseStepDef def) {
        Map<String, Object> result = new HashMap<>();
        int ouId = task.getOuId() != null ? task.getOuId() : 201;
        String periodName = task.getPeriodName();

        // 步骤 → CUX 函数映射
        String checkFunc;
        String detailFunc;
        switch (def.getStepNo()) {
            case 2:
                checkFunc = "CHECK_UNVALIDATED_INVOICES";
                detailFunc = "GET_UNVALIDATED_INVOICE_DETAILS";
                break;
            case 3:
                checkFunc = "CHECK_ON_HOLD_INVOICES";
                detailFunc = "GET_ON_HOLD_INVOICE_DETAILS";
                break;
            default:
                result.put("message", "未知的 AUTO_CHECK 步骤: " + def.getStepNo());
                return result;
        }

        // Step 1: 调用检查函数获取异常数量
        int issueCount = ebsService.callCheckFunction(checkFunc, ouId, periodName);
        result.put("totalCount", issueCount);
        task.setEbsRequestStatus(issueCount > 0 ? "HAS_ISSUES" : "CLEAN");

        // Step 2: 有异常时获取明细
        List<Map<String, Object>> issues = Collections.emptyList();
        if (issueCount > 0) {
            try {
                String detailJson = ebsService.callDetailFunction(detailFunc, ouId, periodName);
                issues = objectMapper.readValue(detailJson,
                        new TypeReference<List<Map<String, Object>>>() {});
            } catch (Exception e) {
                log.warn("[AUTO_CHECK] 获取明细失败: func={}, error={}", detailFunc, e.getMessage());
                result.put("detailError", e.getMessage());
            }
        }
        result.put("issues", issues);
        result.put("message", issueCount == 0
                ? "检查通过，无异常数据"
                : "发现 " + issueCount + " 条异常数据，请在EBS中处理");

        // AUTO_CHECK 结果待人工确认
        task.setStatus("PENDING");
        return result;
    }

    private String getPeriodEndDate(String periodName) {
        // 简单返回当月最后一天（Mock）
        return periodName + "-28";
    }
}
