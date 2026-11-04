package com.hjgd.plm.m04.approval.job;

import com.hjgd.plm.m04.approval.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审批超时扫描。节点上配了 timeout_hours 才参与判定，策略取节点 on_timeout：
 * REMIND 只记日志 / AUTO_PASS 代签并推进 / ESCALATE 待办转给 ADMIN。
 *
 * <p>默认每 10 分钟一次，可用 {@code plm.job.approval.timeout} 覆盖，
 * 设为 {@code -} 则停用（Spring 的 CRON_DISABLED 约定）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalTimeoutJob {

    private final ApprovalService approvalService;

    @Scheduled(cron = "${plm.job.approval.timeout:0 */10 * * * ?}")
    public void scan() {
        try {
            int handled = approvalService.handleTimeouts(LocalDateTime.now());
            if (handled > 0) {
                log.info("[审批超时] 本轮处理 {} 个实例", handled);
            }
        } catch (Exception e) {
            // 定时任务不能把异常抛出去，否则该任务在本进程内不再触发
            log.error("[审批超时] 扫描失败: {}", e.getMessage(), e);
        }
    }
}
