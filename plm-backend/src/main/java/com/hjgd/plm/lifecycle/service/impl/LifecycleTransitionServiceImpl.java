package com.hjgd.plm.lifecycle.service.impl;

import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.bom.entity.Bom;
import com.hjgd.plm.bom.service.BomService;
import com.hjgd.plm.common.ApiErrorCodes;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.ResultCode;
import com.hjgd.plm.event.service.DomainEventService;
import com.hjgd.plm.lifecycle.service.LifecycleService;
import com.hjgd.plm.lifecycle.service.LifecycleTransitionService;
import com.hjgd.plm.material.entity.Material;
import com.hjgd.plm.material.service.MaterialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LifecycleTransitionServiceImpl implements LifecycleTransitionService {

    private final LifecycleService lifecycleService;
    private final MaterialService materialService;
    private final BomService bomService;
    private final DomainEventService domainEventService;

    @Override
    @Transactional
    public TransitionResult transition(String objectType, String objectId, String action,
                                       String comment, boolean force) {
        String type = objectType == null ? "" : objectType.trim().toUpperCase();
        if (!StringUtils.hasText(objectId) || !StringUtils.hasText(action)) {
            throw new BusinessException("objectId 与 action 必填");
        }
        if (force) {
            if (!SecurityUtils.hasRole("ADMIN")) {
                throw new BusinessException(403, "[" + ApiErrorCodes.LIFECYCLE_DENIED + "] force 仅管理员可用");
            }
            // force 的语义是覆盖「where-used 等业务守卫」, 该守卫 Phase 1 才落地;
            // 此处明确拒绝而非静默忽略, 避免调用方误以为已强制放行。
            throw new BusinessException(400, "[" + ApiErrorCodes.LIFECYCLE_DENIED
                    + "] force 覆盖守卫尚未实现(Phase 1 随 where-used 门禁一并提供)");
        }

        String from = currentState(type, objectId);
        // 守卫: 非法流转在此抛 409 LIFECYCLE_DENIED
        String to = lifecycleService.resolveToState(type, from, action);
        if (!lifecycleService.isRoleAllowed(type, from, action)) {
            throw new BusinessException(403, "[" + ApiErrorCodes.LIFECYCLE_DENIED + "] 当前角色无权执行 "
                    + type + " " + action + " (需要: "
                    + String.join("/", lifecycleService.allowedRoles(type, from, action)) + ")");
        }

        // 动作: 复用各领域既有 action 实现 (含其内部 DQ 门禁与副作用)
        String applied = applyAction(type, objectId, action, comment);
        if (!to.equals(applied)) {
            // 矩阵与实际落库状态不一致 = 实现与配置已漂移, 必须暴露而不是放过
            throw new BusinessException(500, "生命周期矩阵与动作实现不一致: 期望 " + to + " 实际 " + applied);
        }

        lifecycleService.recordHistory(type, objectId, from, to, action,
                SecurityUtils.getCurrentRealName(), comment);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("objectType", type);
        payload.put("objectId", objectId);
        payload.put("from", from);
        payload.put("to", to);
        payload.put("action", action);
        payload.put("operator", String.valueOf(SecurityUtils.getCurrentRealName()));
        domainEventService.publish("lifecycle.transitioned", type, objectId, payload);
        log.info("生命周期流转: {}[{}] {} --{}--> {}", type, objectId, from, action, to);
        return new TransitionResult(type, objectId, from, to, action);
    }

    /** 当前状态 (以对象自身为准, 不信任调用方传入) */
    private String currentState(String type, String objectId) {
        if ("PART".equals(type)) {
            Material m = materialService.getByPartNo(objectId);
            if (m == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "料号不存在: " + objectId);
            }
            if (m.getStatus() == null) {
                throw new BusinessException("料号 " + objectId + " 无生命周期状态, 请先修复数据");
            }
            return m.getStatus().name();
        }
        if ("BOM".equals(type)) {
            return resolveBom(objectId).getStatus();
        }
        throw new BusinessException(400, "不支持的 objectType: " + type + " (当前支持 PART/BOM)");
    }

    /**
     * 执行动作并返回落库后的状态。
     * 已实现动作: PART submit_review/release/to_production/obsolete/seal/start_change, BOM release;
     * 其余动作码在矩阵中存在但本版本无实现 (如 finish_change 由 ECN 生效管线承接), 明确拒绝并提示。
     */
    private String applyAction(String type, String objectId, String action, String comment) {
        if ("PART".equals(type)) {
            Material m = materialService.getByPartNo(objectId);
            switch (action) {
                case "submit_review" -> materialService.submitReview(m.getId());
                case "release" -> materialService.release(m.getId());
                case "to_production" -> materialService.toProduction(m.getId());
                case "obsolete" -> materialService.obsolete(m.getId());
                case "seal" -> materialService.seal(m.getId());
                case "start_change" -> materialService.startChange(m.getId());
                default -> throw unsupported(type, action);
            }
            return materialService.getById(m.getId()).getStatus().name();
        }
        if ("BOM".equals(type)) {
            Bom bom = resolveBom(objectId);
            if (!"release".equals(action)) {
                throw unsupported(type, action);
            }
            bomService.release(bom.getId());
            return bomService.getById(bom.getId()).getStatus();
        }
        throw new BusinessException(400, "不支持的 objectType: " + type + " (当前支持 PART/BOM)");
    }

    /** BOM 定位: 先按 BOM 编号, 再按顶级料号 (取最近一条) */
    private Bom resolveBom(String objectId) {
        Bom bom = bomService.getByBomNo(objectId);
        if (bom == null) {
            bom = bomService.getByPartNo(objectId);
        }
        if (bom == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "BOM 不存在: " + objectId);
        }
        return bom;
    }

    private BusinessException unsupported(String type, String action) {
        return new BusinessException(409, "[" + ApiErrorCodes.LIFECYCLE_DENIED + "] 动作无实现, 暂不能经"
                + "通用入口执行: " + type + " " + action);
    }
}
