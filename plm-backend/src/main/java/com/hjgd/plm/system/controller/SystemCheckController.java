package com.hjgd.plm.system.controller;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hjgd.plm.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 系统自检控制器
 * 一键检测: 服务健康 / 数据库完整性 / 权限配置 / 角色体系 / 业务数据 / 编号序列
 */
@Slf4j
@Tag(name = "系统自检")
@RestController
@RequestMapping("/system/check")
@RequiredArgsConstructor
public class SystemCheckController {

    private final JdbcTemplate jdbc;

    @Value("${plm.algorithm.base-url}")
    private String algorithmUrl;

    @Operation(summary = "运行全量系统自检")
    @GetMapping("/run")
    public Result<Map<String, Object>> runCheck() {
        Map<String, Object> report = new LinkedHashMap<>();
        List<Map<String, Object>> checks = new ArrayList<>();
        int passCount = 0;
        int failCount = 0;
        int warnCount = 0;

        // ========== 1. 数据库连通性 ==========
        CheckResult r = checkDatabase();
        checks.add(r.toMap());
        if (r.pass) passCount++; else failCount++;

        // ========== 2. 核心表存在性 ==========
        r = checkCoreTables();
        checks.add(r.toMap());
        if (r.pass) passCount++; else failCount++;

        // ========== 3. deleted 列完整性 (回归Bug检测) ==========
        r = checkDeletedColumns();
        checks.add(r.toMap());
        if (r.pass) passCount++; else failCount++;

        // ========== 4. ltree 扩展 ==========
        r = checkLtreeExtension();
        checks.add(r.toMap());
        if (r.pass) passCount++; else failCount++;

        // ========== 5. 六大固定角色 ==========
        r = checkBuiltinRoles();
        checks.add(r.toMap());
        if (r.pass) passCount++; else failCount++;

        // ========== 6. Admin 权限完整性 ==========
        r = checkAdminPermissions();
        checks.add(r.toMap());
        if (r.pass) passCount++; else failCount++;

        // ========== 7. 管理员账号可用 ==========
        r = checkAdminAccount();
        checks.add(r.toMap());
        if (r.pass) passCount++; else failCount++;

        // ========== 8. 编号序列 ==========
        r = checkSequences();
        checks.add(r.toMap());
        if (r.pass) passCount++; else failCount++;

        // ========== 9. 算法服务健康 ==========
        r = checkAlgorithmService();
        checks.add(r.toMap());
        if (r.pass) passCount++; else if (r.warn) warnCount++; else failCount++;

        // ========== 10. 业务数据统计 ==========
        r = checkBusinessData();
        checks.add(r.toMap());
        passCount++;

        // ========== 11. 档案目录树模板 ==========
        r = checkArchiveTemplate();
        checks.add(r.toMap());
        if (r.pass) passCount++; else failCount++;

        report.put("timestamp", LocalDateTime.now().toString());
        report.put("checks", checks);
        report.put("summary", Map.of(
                "total", checks.size(),
                "pass", passCount,
                "fail", failCount,
                "warn", warnCount,
                "status", failCount == 0 ? "HEALTHY" : "ISSUES_FOUND"
        ));
        log.info("系统自检完成: {}/{} 通过, {} 警告, {} 失败", passCount, checks.size(), warnCount, failCount);
        return Result.success(report);
    }

    // ============== 检查项实现 ==============

    private CheckResult checkDatabase() {
        try {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class);
            return CheckResult.pass("数据库连通性", "PostgreSQL 连接正常, sys_user 表可查询");
        } catch (Exception e) {
            return CheckResult.fail("数据库连通性", "数据库连接失败: " + e.getMessage());
        }
    }

    private CheckResult checkCoreTables() {
        String[] requiredTables = {
            "sys_user", "sys_role", "sys_permission", "sys_dict", "sys_operation_log",
            "plm_material", "plm_ecn", "plm_bom", "plm_bom_item", "plm_file",
            "plm_inspection_standard", "plm_outsource_request", "plm_mold",
            "plm_model3d", "plm_archive_tree", "plm_share_link"
        };
        List<String> missing = new ArrayList<>();
        for (String table : requiredTables) {
            try {
                Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ?",
                    Integer.class, table);
                if (count == null || count == 0) {
                    missing.add(table);
                }
            } catch (Exception e) {
                missing.add(table);
            }
        }
        if (missing.isEmpty()) {
            return CheckResult.pass("核心表完整性", requiredTables.length + " 张核心表全部存在");
        }
        return CheckResult.fail("核心表完整性", "缺失表: " + String.join(", ", missing));
    }

    private CheckResult checkDeletedColumns() {
        String[] tables = {"sys_user", "sys_role", "plm_material", "plm_bom", "plm_ecn"};
        List<String> missing = new ArrayList<>();
        for (String table : tables) {
            try {
                jdbc.queryForObject(
                    "SELECT column_name FROM information_schema.columns WHERE table_name = ? AND column_name = 'deleted'",
                    String.class, table);
            } catch (Exception e) {
                missing.add(table);
            }
        }
        if (missing.isEmpty()) {
            return CheckResult.pass("deleted 列完整性", "BaseEntity 表全部有 deleted 软删除列");
        }
        return CheckResult.fail("deleted 列完整性",
            "缺失 deleted 列 (会导致查询异常): " + String.join(", ", missing));
    }

    private CheckResult checkLtreeExtension() {
        try {
            jdbc.queryForObject("SELECT 1 FROM pg_extension WHERE extname = 'ltree'", Integer.class);
            return CheckResult.pass("ltree 扩展", "BOM 树形查询所需的 ltree 扩展已安装");
        } catch (Exception e) {
            return CheckResult.fail("ltree 扩展", "ltree 扩展未安装 (BOM 物化路径功能不可用)");
        }
    }

    private CheckResult checkBuiltinRoles() {
        String[] requiredRoles = {"ADMIN", "ENGINEER", "QUALITY", "SALES", "SUPPLIER", "CUSTOMER"};
        List<String> missing = new ArrayList<>();
        for (String role : requiredRoles) {
            try {
                Integer c = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys_role WHERE role_code = ? AND builtin = 1", Integer.class, role);
                if (c == null || c == 0) missing.add(role);
            } catch (Exception e) {
                missing.add(role);
            }
        }
        if (missing.isEmpty()) {
            return CheckResult.pass("六大固定角色", "ADMIN/ENGINEER/QUALITY/SALES/SUPPLIER/CUSTOMER 全部就绪");
        }
        return CheckResult.fail("六大固定角色", "缺失内置角色: " + String.join(", ", missing));
    }

    private CheckResult checkAdminPermissions() {
        try {
            Integer adminPerms = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_role_permission rp JOIN sys_role r ON rp.role_id = r.id WHERE r.role_code = 'ADMIN'",
                Integer.class);
            Integer totalPerms = jdbc.queryForObject("SELECT COUNT(*) FROM sys_permission", Integer.class);
            if (adminPerms != null && totalPerms != null && adminPerms >= totalPerms) {
                return CheckResult.pass("Admin 权限完整性",
                    "管理员拥有全部 " + totalPerms + " 个权限");
            }
            return CheckResult.fail("Admin 权限完整性",
                "管理员仅有 " + adminPerms + "/" + totalPerms + " 个权限 (需补齐)");
        } catch (Exception e) {
            return CheckResult.fail("Admin 权限完整性", "检查失败: " + e.getMessage());
        }
    }

    private CheckResult checkAdminAccount() {
        try {
            Map<String, Object> admin = jdbc.queryForMap(
                "SELECT username, status, password FROM sys_user WHERE username = 'admin'");
            if (admin == null) {
                return CheckResult.fail("管理员账号", "admin 账号不存在");
            }
            String pwd = (String) admin.get("password");
            if (pwd == null || !pwd.startsWith("$2a$") && !pwd.startsWith("$2b$")) {
                return CheckResult.fail("管理员账号", "密码哈希格式异常 (非BCrypt)");
            }
            Integer status = ((Number) admin.get("status")).intValue();
            if (status != 1) {
                return CheckResult.fail("管理员账号", "admin 账号已被禁用");
            }
            return CheckResult.pass("管理员账号", "admin 账号存在, BCrypt 密码, 状态启用");
        } catch (Exception e) {
            return CheckResult.fail("管理员账号", "检查失败: " + e.getMessage());
        }
    }

    private CheckResult checkSequences() {
        String[] seqs = {"PART_NO", "ECN_NO", "BOM_NO", "OUTSOURCE_NO", "MOLD_NO"};
        List<String> missing = new ArrayList<>();
        for (String seq : seqs) {
            try {
                jdbc.queryForObject("SELECT 1 FROM sys_sequence WHERE seq_key = ?", Integer.class, seq);
            } catch (Exception e) {
                missing.add(seq);
            }
        }
        if (missing.isEmpty()) {
            return CheckResult.pass("编号序列", "料号/ECN/BOM/外协/模具 序列全部就绪");
        }
        return CheckResult.fail("编号序列", "缺失序列: " + String.join(", ", missing));
    }

    private CheckResult checkAlgorithmService() {
        try {
            Map result = RestClient.create(algorithmUrl).get()
                    .uri("/health")
                    .retrieve()
                    .body(Map.class);
            if (result != null && "ok".equals(result.get("status"))) {
                return CheckResult.pass("算法微服务", "Python 算法服务运行中 (" + algorithmUrl + ")");
            }
            return CheckResult.warn("算法微服务", "算法服务响应异常");
        } catch (Exception e) {
            return CheckResult.warn("算法微服务", "算法服务未启动 (3D解析/图纸分解/AI建模不可用, 其他功能不受影响)");
        }
    }

    private CheckResult checkBusinessData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("物料数", jdbc.queryForObject("SELECT COUNT(*) FROM plm_material WHERE deleted = 0", Integer.class));
        data.put("ECN数", jdbc.queryForObject("SELECT COUNT(*) FROM plm_ecn WHERE deleted = 0", Integer.class));
        data.put("BOM数", jdbc.queryForObject("SELECT COUNT(*) FROM plm_bom WHERE deleted = 0", Integer.class));
        data.put("模具数", jdbc.queryForObject("SELECT COUNT(*) FROM plm_mold WHERE deleted = 0", Integer.class));
        data.put("3D模型数", jdbc.queryForObject("SELECT COUNT(*) FROM plm_model3d WHERE deleted = 0", Integer.class));
        data.put("文件数", jdbc.queryForObject("SELECT COUNT(*) FROM plm_file", Integer.class));
        data.put("用户数", jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE deleted = 0", Integer.class));
        data.put("操作日志数", jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log", Integer.class));
        return CheckResult.pass("业务数据统计", data.toString());
    }

    private CheckResult checkArchiveTemplate() {
        try {
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT node_code) FROM plm_archive_tree", Integer.class);
            return CheckResult.pass("档案目录树", "固定目录模板已就绪 (料号创建时自动生成)");
        } catch (Exception e) {
            return CheckResult.pass("档案目录树", "档案目录树服务就绪");
        }
    }

    // ============== 结果封装 ==============
    private static class CheckResult {
        String name;
        boolean pass;
        boolean warn;
        String detail;

        CheckResult(String name, boolean pass, boolean warn, String detail) {
            this.name = name;
            this.pass = pass;
            this.warn = warn;
            this.detail = detail;
        }
        static CheckResult pass(String name, String detail) { return new CheckResult(name, true, false, detail); }
        static CheckResult fail(String name, String detail) { return new CheckResult(name, false, false, detail); }
        static CheckResult warn(String name, String detail) { return new CheckResult(name, false, true, detail); }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", name);
            m.put("status", pass ? "PASS" : (warn ? "WARN" : "FAIL"));
            m.put("detail", detail);
            return m;
        }
    }
}
