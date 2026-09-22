package com.hjgd.plm.workitem.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hjgd.plm.workitem.entity.WorkItem;
import com.hjgd.plm.workitem.mapper.WorkItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkItemSlaEscalationJob {

    private final WorkItemMapper mapper;

    @Scheduled(cron = "0 */15 * * * ?")
    public void escalate() {
        LocalDateTime now = LocalDateTime.now();
        List<WorkItem> overdue = mapper.selectList(
                new LambdaQueryWrapper<WorkItem>()
                        .eq(WorkItem::getStatus, "OPEN")
                        .lt(WorkItem::getSlaDueAt, now));
        if (overdue.isEmpty()) return;
        for (WorkItem item : overdue) {
            item.setStatus("ESCALATED");
            mapper.updateById(item);
        }
        log.info("[SLA] escalated {} work items", overdue.size());
    }
}
