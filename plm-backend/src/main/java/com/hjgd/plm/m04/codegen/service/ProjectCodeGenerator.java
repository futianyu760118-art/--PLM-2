package com.hjgd.plm.m04.codegen.service;

import com.hjgd.plm.common.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 项目号生成器 —— 规则 HJ.AAXXX.Y1.ZZZ
 *
 * <pre>
 *   HJ   恒剑
 *   AA   年份两位（22 = 2022）
 *   XXX  项目序列号 001~999，按立项时间先后，年内重置
 *   Y1   产品分类 + 版本号
 *          V = 全新产品，R = 功能/性能拓展，M = 客户定制需求变化
 *   ZZZ  客户编码段，三级结构（2026-10-31 业务方确认）：
 *          第 1 级  客户代码 3~4 位数字            256 / 585 / 1008 / 000（自研）
 *          第 2 级  分支：客户码+字母标签           256JY / 256YL
 *                   或 客户码+-N                   585-1 / 000-2
 *          第 3 级  分支的分支：客户码+标签+-N      256JY-1
 *   例：HJ.22001.R1.631 = 22 年第 1 个项目，功能扩展版，631 客户定向开发
 *       HJ.26031.M1.585-1 = 585 客户定制版第 1 个分支
 *       HJ.23028.R1.256JY-1 = 256 客户 JY 分支下的第 1 个子分支
 * </pre>
 *
 * 序号口径：不用独立计数器，直接取当年库内已占用的最大序号 +1，
 * 避免与存量 162 个老编号撞号（老编号不经过本系统生成）。
 *
 * 并发：事务级 advisory lock 串行化，防止并发立项取到同一序号。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectCodeGenerator {

    /**
     * 项目号严格格式
     * HJ.{2位年}{3位序号}.{VRM}{1位版本}.{3~4位客户码}[字母分支标签][-N 分支序号]
     * 例：HJ.26001.R1.631 / HJ.26031.M1.585-1 / HJ.23028.R1.256JY-1
     */
    public static final Pattern STRICT = Pattern.compile(
            "^HJ\\.\\d{2}\\d{3}\\.[VRM]\\d\\.\\d{3,4}([A-Z]{1,2})?(-\\d+)?$");

    /** 客户编码段：数字 1~4 位 + 可选字母分支标签 + 可选 -N 分支序号（前缀匹配，容忍尾部备注） */
    private static final Pattern CUSTOMER_SEG = Pattern.compile(
            "^(\\d{1,4})([A-Z]{1,2})?(-\\d+)?");

    private static final Pattern DASH_SEQ_TAIL = Pattern.compile("-\\d+$");
    private static final Pattern BRANCH_SEQ = Pattern.compile("-(\\d+)$");

    private static final Set<String> CLASSES = Set.of("V", "R", "M");
    private static final String SELF_CUSTOMER = "000";
    private static final long LOCK_KEY = 7612001L;
    private static final int MAX_SEQ = 999;

    private final JdbcTemplate jdbc;

    @Transactional(propagation = Propagation.MANDATORY)
    public String next(String customerCode, String projectClass, Integer classVersion) {
        // 事务级排他锁：并发立项时串行取号（连接释放/事务结束自动解锁）
        jdbc.execute("SELECT pg_advisory_xact_lock(" + LOCK_KEY + ")");

        String yy = String.format("%02d", LocalDate.now().getYear() % 100);
        int seq = nextSeq(yy);

        String cls = normalizeClass(projectClass);
        int ver = (classVersion == null || classVersion < 1 || classVersion > 9) ? 1 : classVersion;
        String cust = normalizeCustomer(customerCode);

        String base = "HJ." + yy + String.format("%03d", seq) + "." + cls + ver + "." + cust;
        // 客户段应取"客户代码"，若误传了带分支的整段，冲突时在分支序号上递增，不叠加 -N-N
        String stem = DASH_SEQ_TAIL.matcher(base).replaceAll("");
        java.util.regex.Matcher tail = BRANCH_SEQ.matcher(base);
        int branchNo = tail.find() ? Integer.parseInt(tail.group(1)) : 0;

        String code = base;
        while (exists(code)) {
            branchNo++;
            if (branchNo > 99) {
                throw new BusinessException("项目号生成失败：同年同客户同版本已存在 99 个子分支，请手工处理 " + base);
            }
            code = stem + "-" + branchNo;
        }
        log.info("[项目号] 生成 {} (年{} 序号{} 分类{}{} 客户{})", code, yy, seq, cls, ver, cust);
        return code;
    }

    /** 当年下一个可用序号：库内该年最大序号 + 1 */
    private int nextSeq(String yy) {
        Integer max = jdbc.queryForObject(
                "SELECT COALESCE(MAX(substring(project_no from 3 for 3)::int), 0) " +
                "FROM plm_project WHERE project_no ~ ('^HJ\\.' || ? || '[0-9]{3}\\.')",
                Integer.class, yy);
        int seq = (max == null ? 0 : max) + 1;
        if (seq > MAX_SEQ) {
            throw new BusinessException("项目号年度序号已用尽（" + MAX_SEQ + "），需扩展位数或改用下一年度");
        }
        return seq;
    }

    private boolean exists(String code) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(1) FROM plm_project WHERE project_no = ?", Integer.class, code);
        return n != null && n > 0;
    }

    /** 分类归一：优先取值本身，其次按项目类型推断 */
    public static String normalizeClass(String projectClass) {
        if (projectClass != null) {
            String c = projectClass.trim().toUpperCase();
            if (CLASSES.contains(c)) return c;
            if (c.contains("客制") || c.contains("定制") || c.contains("CUSTOM") || "M".equals(c)) return "M";
            if (c.contains("改") || c.contains("拓展") || c.contains("升级") || c.contains("IMPROVE")) return "R";
        }
        return "V";
    }

    /**
     * 客户编码段归一：保留三级结构
     * <pre>
     *   自研/空                -> 000
     *   "256jy" / " 256JY -2"  -> 256JY / 256JY-2
     *   "1008" / "1008-1"      -> 1008   / 1008-1
     *   "46"                   -> 046（不足 3 位补零）
     * </pre>
     */
    public static String normalizeCustomer(String raw) {
        if (!StringUtils.hasText(raw)) return SELF_CUSTOMER;
        String s = raw.trim();
        if (s.contains("自研") || s.contains("自主")) return SELF_CUSTOMER;

        // 去全角括号/空白，仅保留 数字 + 字母 + -数字
        s = s.replace('（', '(').replace('）', ')')
             .replaceAll("\\s+", "").toUpperCase();
        java.util.regex.Matcher m = CUSTOMER_SEG.matcher(s);
        if (!m.find()) return SELF_CUSTOMER;

        String digits = m.group(1);
        if (digits.length() > 4) digits = digits.substring(0, 4);
        if (digits.length() < 3) digits = String.format("%3s", digits).replace(' ', '0');

        String tag = m.group(2) == null ? "" : m.group(2);
        String seq = m.group(3) == null ? "" : m.group(3);
        return digits + tag + seq;
    }

    /** 手工录入编号的合规校验 */
    public static void validateManual(String projectNo) {
        if (!StringUtils.hasText(projectNo)) return;
        if (!STRICT.matcher(projectNo.trim()).matches()) {
            throw new BusinessException(
                    "项目号不符合规则 HJ.AAXXX.Y1.ZZZ（例 HJ.26001.R1.631）：" + projectNo);
        }
    }
}
