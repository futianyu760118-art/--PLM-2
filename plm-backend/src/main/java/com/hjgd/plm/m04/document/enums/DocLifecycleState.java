package com.hjgd.plm.m04.document.enums;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 文档生命周期状态 = 老 PLM 的五区。
 *
 * <p>流转规则集中在 {@link #ALLOWED} 里，service 只查表不写 if-else，
 * 后续要加区（例如"评审中"）只改这里。
 */
public enum DocLifecycleState {

    /** 工作区：起草/修改中，可检出 */
    WORKING,
    /** 发布区：生效版本，只读 */
    RELEASED,
    /** 归档区：项目结束留存，只读 */
    ARCHIVED,
    /** 报废区：已失效但仍可查 */
    OBSOLETE,
    /** 回收区：逻辑删除前的中转站 */
    RECYCLE;

    /** 允许的流转（KEY -> 可去的状态集合） */
    private static final Map<DocLifecycleState, Set<DocLifecycleState>> ALLOWED;

    static {
        Map<DocLifecycleState, Set<DocLifecycleState>> m = new EnumMap<>(DocLifecycleState.class);
        m.put(WORKING, EnumSet.of(RELEASED, RECYCLE));
        m.put(RELEASED, EnumSet.of(ARCHIVED, OBSOLETE, WORKING));
        m.put(ARCHIVED, EnumSet.of(RELEASED, RECYCLE));
        m.put(OBSOLETE, EnumSet.of(RECYCLE, WORKING));
        m.put(RECYCLE, EnumSet.of(WORKING));
        ALLOWED = Collections.unmodifiableMap(m);
    }

    public boolean canMoveTo(DocLifecycleState target) {
        if (target == null || target == this) {
            return false;
        }
        return ALLOWED.getOrDefault(this, Collections.emptySet()).contains(target);
    }

    public Set<DocLifecycleState> nextStates() {
        return ALLOWED.getOrDefault(this, Collections.emptySet());
    }

    /** 只读区：禁止改元数据、禁止检出 */
    public boolean isReadOnly() {
        return this == RELEASED || this == ARCHIVED || this == OBSOLETE;
    }

    public static DocLifecycleState parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim().toUpperCase();
        for (DocLifecycleState v : values()) {
            if (v.name().equals(s)) {
                return v;
            }
        }
        return null;
    }
}
