package com.autoapcls.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * Oracle EBS 集成服务
 * 通过 JDBC 直连 EBS 数据库，执行查询、提交并发请求、轮询状态、获取输出文件
 */
@Slf4j
@Service
public class EBSIntegrationService {

    private final NamedParameterJdbcTemplate ebsJdbc;
    private final DataSource ebsDataSource;

    public EBSIntegrationService(
            @Qualifier("ebsNamedJdbcTemplate") NamedParameterJdbcTemplate ebsJdbc,
            @Qualifier("ebsDataSource") DataSource ebsDataSource) {
        this.ebsJdbc = ebsJdbc;
        this.ebsDataSource = ebsDataSource;
    }

    // ──────────── CUX_AP_CLOSE_UTIL 包函数调用 ────────────

    /**
     * 调用 CUX_AP_CLOSE_UTIL 包中返回 NUMBER 的函数
     * 格式：{ ? = call APPS.CUX_AP_CLOSE_UTIL.FUNC_NAME(?, ?) }
     */
    public int callCheckFunction(String funcName, int orgId, String periodName) {
        log.info("[EBS] 调用检查函数: {}(orgId={}, period={})", funcName, orgId, periodName);
        String sql = "{ ? = call APPS.CUX_AP_CLOSE_UTIL." + funcName + "(?, ?) }";
        try {
            Connection con = DataSourceUtils.getConnection(ebsDataSource);
            try {
                CallableStatement cs = con.prepareCall(sql);
                cs.registerOutParameter(1, Types.NUMERIC);
                cs.setInt(2, orgId);
                cs.setString(3, periodName);
                cs.execute();
                int result = cs.getInt(1);
                log.info("[EBS] {} 返回: {}", funcName, result);
                return result;
            } finally {
                DataSourceUtils.releaseConnection(con, ebsDataSource);
            }
        } catch (Exception e) {
            log.error("[EBS] 调用 {} 失败: {}", funcName, e.getMessage(), e);
            throw new RuntimeException("EBS 检查失败: " + e.getMessage(), e);
        }
    }

    /**
     * 调用 CUX_AP_CLOSE_UTIL 包中返回 CLOB 的函数（JSON 格式明细数据）
     */
    public String callDetailFunction(String funcName, int orgId, String periodName) {
        log.info("[EBS] 调用明细函数: {}(orgId={}, period={})", funcName, orgId, periodName);
        String sql = "{ ? = call APPS.CUX_AP_CLOSE_UTIL." + funcName + "(?, ?) }";
        try {
            Connection con = DataSourceUtils.getConnection(ebsDataSource);
            try {
                CallableStatement cs = con.prepareCall(sql);
                cs.registerOutParameter(1, Types.CLOB);
                cs.setInt(2, orgId);
                cs.setString(3, periodName);
                cs.execute();
                Clob clob = cs.getClob(1);
                if (clob == null) {
                    return "[]";
                }
                String result = clob.getSubString(1, (int) clob.length());
                log.info("[EBS] {} 返回: {} 字符", funcName, result.length());
                return result;
            } finally {
                DataSourceUtils.releaseConnection(con, ebsDataSource);
            }
        } catch (Exception e) {
            log.error("[EBS] 调用 {} 失败: {}", funcName, e.getMessage(), e);
            throw new RuntimeException("EBS 查询明细失败: " + e.getMessage(), e);
        }
    }

    // ──────────── SQL 查询 ────────────

    /**
     * 执行 EBS 查询，使用命名参数（:paramName）
     */
    public List<Map<String, Object>> executeQuery(String sql, Map<String, Object> params) {
        log.debug("[EBS] 执行查询: sql={}, params={}",
                sql.length() > 120 ? sql.substring(0, 120) + "..." : sql, params);
        try {
            List<Map<String, Object>> result = ebsJdbc.queryForList(sql, params != null ? params : Map.of());
            log.debug("[EBS] 查询结果: {} 行", result.size());
            return result;
        } catch (Exception e) {
            log.error("[EBS] 查询失败: {}", e.getMessage(), e);
            throw new RuntimeException("EBS 查询失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行 EBS 查询（无参数便捷方法）
     */
    public List<Map<String, Object>> executeQuery(String sql) {
        return executeQuery(sql, Map.of());
    }

    // ──────────── 并发请求 ────────────

    /**
     * 提交 EBS 并发请求
     * @param programName 并发程序简称，格式如 "CUX:应付发票信息报表"（应用简称:程序简称）
     * @param params      请求参数（按 argument1..argumentN 传入，最多 20 个）
     * @return EBS request_id
     */
    public Long submitConcurrentRequest(String programName, Map<String, Object> params) {
        log.info("[EBS] 提交并发请求: program={}, params={}", programName, params);

        String appName;
        String progName;
        if (programName.contains(":")) {
            String[] parts = programName.split(":", 2);
            appName = parts[0].trim();
            progName = parts[1].trim();
        } else {
            appName = "CUX";
            progName = programName;
        }

        // 构建参数列表（最多 20 个）
        List<Object> args = new ArrayList<>();
        if (params != null) {
            args.addAll(params.values());
        }

        // 调用 FND_REQUEST.SUBMIT_REQUEST
        String plsql = buildSubmitPlsql(appName, progName, args);

        try {
            Connection con = DataSourceUtils.getConnection(ebsDataSource);
            try {
                CallableStatement cs = con.prepareCall(plsql);
                cs.registerOutParameter(1, Types.NUMERIC);
                cs.setString(2, appName);
                cs.setString(3, progName);
                cs.setString(4, "AutoAPCLS: " + progName);
                // 参数从第 5 个位置开始
                for (int i = 0; i < Math.min(args.size(), 20); i++) {
                    Object val = args.get(i);
                    if (val != null) {
                        cs.setString(5 + i, val.toString());
                    } else {
                        cs.setNull(5 + i, Types.VARCHAR);
                    }
                }
                cs.execute();
                long requestId = cs.getLong(1);
                log.info("[EBS] 并发请求已提交: requestId={}", requestId);
                return requestId;
            } finally {
                DataSourceUtils.releaseConnection(con, ebsDataSource);
            }
        } catch (Exception e) {
            log.error("[EBS] 提交并发请求失败: program={}, error={}", programName, e.getMessage(), e);
            throw new RuntimeException("EBS 并发请求提交失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构建 FND_REQUEST.SUBMIT_REQUEST PL/SQL 调用
     */
    private String buildSubmitPlsql(String appName, String programName, List<Object> args) {
        StringBuilder sb = new StringBuilder();
        sb.append("{ ? = call APPS.FND_REQUEST.SUBMIT_REQUEST(");
        sb.append("?, "); // application
        sb.append("?, "); // program
        sb.append("?, "); // description
        sb.append("SYSDATE, "); // start_time
        sb.append("FALSE");     // sub_request
        // 追加 argument1..argumentN
        for (int i = 0; i < Math.min(args.size(), 20); i++) {
            sb.append(", ?");
        }
        sb.append(") }");
        return sb.toString();
    }

    // ──────────── 请求状态 ────────────

    /**
     * 查询 EBS 并发请求状态
     */
    public Map<String, String> getRequestStatus(Long requestId) {
        log.debug("[EBS] 查询请求状态: requestId={}", requestId);
        try {
            String sql = """
                    SELECT request_id, phase_code, status_code, completion_text
                    FROM apps.fnd_concurrent_requests
                    WHERE request_id = :requestId
                    """;
            List<Map<String, Object>> rows = ebsJdbc.queryForList(sql, Map.of("requestId", requestId));
            if (rows.isEmpty()) {
                log.warn("[EBS] 未找到并发请求: requestId={}", requestId);
                return Map.of(
                        "requestId", String.valueOf(requestId),
                        "phaseCode", "U",
                        "statusCode", "U",
                        "completionText", "请求未找到"
                );
            }
            Map<String, Object> row = rows.get(0);
            Map<String, String> result = new HashMap<>();
            result.put("requestId", String.valueOf(row.get("request_id")));
            result.put("phaseCode", String.valueOf(row.get("phase_code")));
            result.put("statusCode", String.valueOf(row.get("status_code")));
            result.put("completionText", row.get("completion_text") != null
                    ? row.get("completion_text").toString() : "");
            return result;
        } catch (Exception e) {
            log.error("[EBS] 查询请求状态失败: requestId={}, error={}", requestId, e.getMessage(), e);
            return Map.of(
                    "requestId", String.valueOf(requestId),
                    "phaseCode", "E",
                    "statusCode", "E",
                    "completionText", "查询失败: " + e.getMessage()
            );
        }
    }

    // ──────────── 输出文件 ────────────

    /**
     * 获取并发请求的输出文件内容
     */
    public byte[] getRequestOutput(Long requestId) {
        log.info("[EBS] 获取输出文件: requestId={}", requestId);
        try {
            // 优先从 FND_CONCURRENT_OUTPUT 获取文件数据
            String sql = """
                    SELECT fco.file_data, fco.file_name
                    FROM apps.fnd_concurrent_output fco
                    WHERE fco.request_id = :requestId
                    ORDER BY fco.output_id
                    """;
            List<Map<String, Object>> outputs = ebsJdbc.queryForList(sql, Map.of("requestId", requestId));
            if (outputs.isEmpty()) {
                // 尝试从 fnd_concurrent_requests 获取输出日志
                String logSql = """
                        SELECT fcr.logfile_name, fcr.outfile_name
                        FROM apps.fnd_concurrent_requests fcr
                        WHERE fcr.request_id = :requestId
                        """;
                List<Map<String, Object>> logInfo = ebsJdbc.queryForList(logSql, Map.of("requestId", requestId));
                if (logInfo.isEmpty()) {
                    log.warn("[EBS] 未找到请求输出: requestId={}", requestId);
                    return ("No output found for request " + requestId).getBytes();
                }
                Map<String, Object> info = logInfo.get(0);
                return ("Request " + requestId + " - Output: " + info.get("outfile_name")).getBytes();
            }

            Map<String, Object> output = outputs.get(0);
            Object fileData = output.get("file_data");
            if (fileData instanceof byte[]) {
                return (byte[]) fileData;
            }
            // BLOB/CLOB 等情况，返回基本信息
            String fileName = output.get("file_name") != null ? output.get("file_name").toString() : "unknown";
            return ("Output file: " + fileName + " (requestId=" + requestId + ")").getBytes();
        } catch (Exception e) {
            log.error("[EBS] 获取输出文件失败: requestId={}, error={}", requestId, e.getMessage(), e);
            return ("Failed to retrieve output: " + e.getMessage()).getBytes();
        }
    }
}
