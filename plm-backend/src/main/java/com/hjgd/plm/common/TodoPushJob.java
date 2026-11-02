package com.hjgd.plm.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每日定时按人推送待办到企业微信（默认每天 08:30，可用 plm.notify.todo-cron 覆盖）。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TodoPushJob {

    private final TodoPushService todoPushService;

    @Scheduled(cron = "${plm.notify.todo-cron:0 30 8 * * ?}")
    public void run() {
        try {
            todoPushService.pushAll();
        } catch (Exception e) {
            log.warn("[待办推送] 定时任务异常: {}", e.getMessage());
        }
    }
}
