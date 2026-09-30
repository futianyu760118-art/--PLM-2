package com.hjgd.plm.feedback;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** 反馈模块常量:类型/状态取值与状态机,与字典 feedback_type / feedback_status 一致 */
public final class FeedbackConstants {

    private FeedbackConstants() {
    }

    public static final String TYPE_BUG = "BUG";
    public static final String TYPE_SUGGEST = "SUGGEST";
    public static final String TYPE_REQUIREMENT = "REQUIREMENT";
    public static final Set<String> TYPES = Set.of(TYPE_BUG, TYPE_SUGGEST, TYPE_REQUIREMENT);

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_EVALUATING = "EVALUATING";
    public static final String STATUS_INITIATED = "INITIATED";
    public static final String STATUS_RETURNED = "RETURNED";
    public static final Set<String> STATUSES = Set.of(
            STATUS_PENDING, STATUS_EVALUATING, STATUS_INITIATED, STATUS_RETURNED);

    /** 状态机:仅 PENDING → EVALUATING → {INITIATED, RETURNED}(AC F13) */
    public static final Map<String, List<String>> TRANSITIONS = Map.of(
            STATUS_PENDING, List.of(STATUS_EVALUATING),
            STATUS_EVALUATING, List.of(STATUS_INITIATED, STATUS_RETURNED),
            STATUS_INITIATED, List.of(),
            STATUS_RETURNED, List.of());

    /** 有效期 10-2000,与 AC F5 一致 */
    public static final int CONTENT_MIN = 10;
    public static final int CONTENT_MAX = 2000;
    public static final int EXPECTED_MAX = 2000;
    public static final int CONTACT_MAX = 100;
    public static final int PAGE_PATH_MAX = 512;

    /** 去重时间窗口(秒),与 AC F11 假设 A5 一致 */
    public static final long DEDUP_WINDOW_SECONDS = 300L;
}
