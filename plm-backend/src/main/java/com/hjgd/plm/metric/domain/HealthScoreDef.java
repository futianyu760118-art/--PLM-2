package com.hjgd.plm.metric.domain;

/**
 * 项目健康分口径定义（单一事实源）。
 *
 * <p>背景（ISS-002 / ISS-003）：原实现的变更因子 CF 以「节点编辑次数」近似变更次数，
 * 完整率 CR 实为「计划日期覆盖率」而非「必填字段完整率」，指标对外口径与实际计算不一致，
 * 会误导管理判断。本类把口径定义、取值来源、阈值常数集中到一处，实现侧只能引用本类，
 * 使「定义式」与「实现值」不存在第二个来源，从而满足 F10 验收口径：
 * 「对同一数据集，定义式计算值与实现值偏差 = 0」。
 *
 * <p>阈值口径（PAND-51 待澄清 6）：无历史基线时先定义采集方法、不编造历史数值；
 * 本类中的常数即 G1/G2 评审前固定的阈值，评审调整只改本类，不改计算路径。
 *
 * <p>健康分公式：
 * <pre>
 *   health = 100 * (0.35*ncr + 0.25*kcr + 0.20*otr + 0.10*cr + 0.10*cf)
 * </pre>
 * 其中 ncr=节点完成率，kcr=关键节点完成率，otr=准时率，cr=必填字段完整率，cf=变更因子。
 * 结果裁剪到 [0,100]。
 */
public final class HealthScoreDef {

    private HealthScoreDef() {}

    // ------------------------------------------------------------------
    // 权重（G1/G2 评审前固定，唯一来源）
    // ------------------------------------------------------------------
    public static final double W_NODE_COMPLETION = 0.35;
    public static final double W_KEY_COMPLETION = 0.25;
    public static final double W_ON_TIME = 0.20;
    public static final double W_FIELD_COMPLETENESS = 0.10;
    public static final double W_CHANGE_FACTOR = 0.10;

    // ------------------------------------------------------------------
    // 阈值（G1/G2 评审前固定，唯一来源）
    // ------------------------------------------------------------------
    /**
     * 变更因子归一化上限：正式变更次数达到该值时 CF = 0。
     * 无历史基线，沿用原实现量纲（50）并在此固定，不作为历史事实引用。
     */
    public static final int CHANGE_FACTOR_CEILING = 50;

    /** 健康分裁剪区间。 */
    public static final int SCORE_MIN = 0;
    public static final int SCORE_MAX = 100;

    /**
     * 采集方法（写入 plm_metric_object.baseline_method）。
     * 数据来源均为 PLM2 既有表，不引入第二事实源。
     */
    public static final String COLLECTION_METHOD =
            "按项目逐条计算：ncr=done/total、kcr=keyDone/keyTotal、otr=onTime/doneWithDate、"
                    + "cr=必填字段满足数/必填字段总数、cf=max(0,1-正式变更次数/" + CHANGE_FACTOR_CEILING + ")；"
                    + "done 取 plm_project_node.status='DONE'；准时判定 actual_date<=plan_date；"
                    + "正式变更次数取 plm_project_change_log 中 action in ('CREATE','UPDATE','DELETE')；"
                    + "health=100*(0.35*ncr+0.25*kcr+0.20*otr+0.10*cr+0.10*cf)";

    // ------------------------------------------------------------------
    // 项目变更分类完整取值集（F10 AC：记录项目变更分类的完整取值集）
    // ------------------------------------------------------------------

    /**
     * 项目变更分类。{@code formal=true} 表示「正式变更」，计入变更因子 CF；
     * {@code formal=false} 为派生（工作表汇总回写）或录入（跟踪总表内联编辑），不计入。
     *
     * <p>取值集与 {@code plm_project_change_log.action} 的取值一一对应，新增动作码必须在此登记，
     * 否则 {@link #fromAction(String)} 返回 {@code null} 并被计入「未分类」，由自检暴露。
     */
    public enum ChangeCategory {
        CREATE("CREATE", true, "新增"),
        UPDATE("UPDATE", true, "修改"),
        DELETE("DELETE", true, "删除"),
        ROLLUP("ROLLUP", false, "工作表汇总派生"),
        CELL("CELL", false, "跟踪总表内联编辑");

        private final String actionCode;
        private final boolean formal;
        private final String label;

        ChangeCategory(String actionCode, boolean formal, String label) {
            this.actionCode = actionCode;
            this.formal = formal;
            this.label = label;
        }

        public String actionCode() { return actionCode; }

        /** 是否为正式变更（计入 CF）。 */
        public boolean isFormal() { return formal; }

        public String label() { return label; }

        /** 按动作码解析分类；未登记的动作码返回 {@code null}。 */
        public static ChangeCategory fromAction(String action) {
            if (action == null) return null;
            for (ChangeCategory c : values()) {
                if (c.actionCode.equalsIgnoreCase(action.trim())) return c;
            }
            return null;
        }
    }

    /**
     * 节点必填字段。完整率 CR = 满足的必填项数 / 应满足的必填项总数。
     * <p>取值来源均为 {@code plm_project_node} 既有列，不新增采集口径。
     */
    public enum RequiredField {
        /** 全部节点：状态必须已设置（非空且非 NOT_SET）。 */
        STATUS_SET("status", false, false),
        /** 全部节点：节点名称非空。 */
        NODE_NAME("node_name", false, false),
        /** 全部节点：顺序号非空（决定跟踪总表列位）。 */
        SEQ("seq", false, false),
        /** 关键节点：计划日期非空。 */
        PLAN_DATE("plan_date", true, false),
        /** 关键节点：责任人非空。 */
        OWNER("owner", true, false),
        /** 已完成节点：实际完成日期非空。 */
        ACTUAL_DATE("actual_date", false, true),
        /** 已完成节点：证据文件数 >= 1。 */
        EVIDENCE("evidence_count", false, true);

        private final String column;
        private final boolean keyNodeOnly;
        private final boolean doneOnly;

        RequiredField(String column, boolean keyNodeOnly, boolean doneOnly) {
            this.column = column;
            this.keyNodeOnly = keyNodeOnly;
            this.doneOnly = doneOnly;
        }

        public String column() { return column; }

        /** 是否仅对关键节点要求。 */
        public boolean keyNodeOnly() { return keyNodeOnly; }

        /** 是否仅对已完成（DONE）节点要求。 */
        public boolean doneOnly() { return doneOnly; }

        /** 该必填项在当前节点状态下是否应被满足。 */
        public boolean appliesTo(boolean isKeyNode, boolean isDone) {
            if (keyNodeOnly && !isKeyNode) return false;
            if (doneOnly && !isDone) return false;
            return true;
        }
    }

    /** 该节点状态下应满足的必填项总数。 */
    public static int requiredFieldCount(boolean isKeyNode, boolean isDone) {
        int n = 0;
        for (RequiredField f : RequiredField.values()) {
            if (f.appliesTo(isKeyNode, isDone)) n++;
        }
        return n;
    }

    // ------------------------------------------------------------------
    // 定义式（实现的唯一来源）
    // ------------------------------------------------------------------

    /** 节点完成率 ncr = 已完成节点数 / 节点总数。无节点时为 0。 */
    public static double nodeCompletionRate(int done, int total) {
        return total <= 0 ? 0.0 : (double) done / total;
    }

    /** 关键节点完成率 kcr = 已完成关键节点数 / 关键节点总数。无关键节点时为 0。 */
    public static double keyCompletionRate(int keyDone, int keyTotal) {
        return keyTotal <= 0 ? 0.0 : (double) keyDone / keyTotal;
    }

    /** 准时率 otr = 准时完成数 / 有实际日期的完成数。无分母时按 1.0（无逾期即满分）。 */
    public static double onTimeRate(int onTime, int doneWithDate) {
        return doneWithDate <= 0 ? 1.0 : (double) onTime / doneWithDate;
    }

    /** 必填字段完整率 cr = 满足的必填项数 / 应满足的必填项总数。无必填项时为 0。 */
    public static double fieldCompleteness(long satisfied, long required) {
        return required <= 0 ? 0.0 : (double) satisfied / required;
    }

    /** 变更因子 cf = max(0, 1 - 正式变更次数 / 上限)。正式变更次数取负值按 0 处理。 */
    public static double changeFactor(int formalChangeCount) {
        if (formalChangeCount <= 0) return 1.0;
        return Math.max(0.0, 1.0 - (double) formalChangeCount / CHANGE_FACTOR_CEILING);
    }

    /** 健康分。入参均为比率/因子，返回裁剪到 [0,100] 的整数分。 */
    public static int health(double ncr, double kcr, double otr, double cr, double cf) {
        double raw = 100.0 * (W_NODE_COMPLETION * ncr
                + W_KEY_COMPLETION * kcr
                + W_ON_TIME * otr
                + W_FIELD_COMPLETENESS * cr
                + W_CHANGE_FACTOR * cf);
        return (int) Math.round(Math.max(SCORE_MIN, Math.min(SCORE_MAX, raw)));
    }

    /** 保留 3 位小数（与既有对外口径一致）。 */
    public static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
