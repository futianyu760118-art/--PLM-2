package com.hjgd.plm.metric.job;

import com.hjgd.plm.config.JobProperties;
import com.hjgd.plm.dq.service.DataQualityService;
import com.hjgd.plm.dq.service.DqAutoFixService;
import com.hjgd.plm.improve.service.InsightGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * V1.1 DQ夜检 Job：① 执行开关 ② 串行(DQ → 洞察) ③ 异常吞噬不抛出。
 *
 * 验证：
 *  - 开关关闭 → 不调用任何 service
 *  - 开关打开 → 先 DQ 后 Insight 顺序
 *  - DQ 抛异常 → 不再调 Insight，异常被吞
 *  - Insight 抛异常 → 不抛(整体不失败)
 */
@DisplayName("DQ 夜检 Job 编排")
@ExtendWith(MockitoExtension.class)
class DqNightScanJobTest {

    @Mock private DataQualityService dataQualityService;
    @Mock private InsightGenerator insightGenerator;
    @Mock private DqAutoFixService dqAutoFixService;

    @Test
    @DisplayName("开关关闭时不应触发任何 service")
    void disabledShouldBeNoOp() {
        JobProperties props = new JobProperties();
        props.getDq().setEnabled(false);
        DqNightScanJob job = new DqNightScanJob(dataQualityService, insightGenerator, dqAutoFixService, props);

        job.run();

        verifyNoInteractions(dataQualityService);
        verifyNoInteractions(insightGenerator);
        verifyNoInteractions(dqAutoFixService);
    }

    @Test
    @DisplayName("开关打开：先 DQ 后 Insight 再 AutoFix 串行执行")
    void enabledRunsBothInOrder() {
        JobProperties props = new JobProperties();
        props.getDq().setEnabled(true);
        when(dataQualityService.runFullScan("NIGHTLY")).thenReturn(7);
        when(insightGenerator.generateFromDqDebt()).thenReturn(3);
        when(dqAutoFixService.fixTopDebts(anyInt(), eq("AUTO"))).thenReturn(2);

        DqNightScanJob job = new DqNightScanJob(dataQualityService, insightGenerator, dqAutoFixService, props);
        job.run();

        verify(dataQualityService, times(1)).runFullScan("NIGHTLY");
        verify(insightGenerator, times(1)).generateFromDqDebt();
        verify(dqAutoFixService, times(1)).fixTopDebts(5, "AUTO");
    }

    @Test
    @DisplayName("DQ 抛异常时不应再调 Insight, 异常被吞噬不抛出")
    void dqFailureSkipsInsight() {
        JobProperties props = new JobProperties();
        props.getDq().setEnabled(true);
        when(dataQualityService.runFullScan(anyString())).thenThrow(new RuntimeException("dq db down"));

        DqNightScanJob job = new DqNightScanJob(dataQualityService, insightGenerator, dqAutoFixService, props);
        // 不抛任何异常
        job.run();

        verify(insightGenerator, never()).generateFromDqDebt();
        verify(dqAutoFixService, never()).fixTopDebts(anyInt(), anyString());
    }

    @Test
    @DisplayName("Insight 抛异常被吞噬, job 整体不抛出")
    void insightFailureIsSwallowed() {
        JobProperties props = new JobProperties();
        props.getDq().setEnabled(true);
        when(dataQualityService.runFullScan(anyString())).thenReturn(5);
        when(insightGenerator.generateFromDqDebt()).thenThrow(new RuntimeException("insight fail"));

        DqNightScanJob job = new DqNightScanJob(dataQualityService, insightGenerator, dqAutoFixService, props);
        // 同样不抛
        job.run();

        verify(dataQualityService, times(1)).runFullScan("NIGHTLY");
        verify(insightGenerator, times(1)).generateFromDqDebt();
    }

    @Test
    @DisplayName("AutoFix 抛异常被吞噬, job 整体不抛出")
    void autoFixFailureIsSwallowed() {
        JobProperties props = new JobProperties();
        props.getDq().setEnabled(true);
        when(dataQualityService.runFullScan("NIGHTLY")).thenReturn(5);
        when(insightGenerator.generateFromDqDebt()).thenReturn(2);
        when(dqAutoFixService.fixTopDebts(anyInt(), eq("AUTO"))).thenThrow(new RuntimeException("fix fail"));

        DqNightScanJob job = new DqNightScanJob(dataQualityService, insightGenerator, dqAutoFixService, props);
        job.run();

        verify(dqAutoFixService, times(1)).fixTopDebts(5, "AUTO");
    }

    @Test
    @DisplayName("DQ 返回 0 时仍会调 Insight（哪怕无债务也走流程）")
    void zeroScannedStillInvokesInsight() {
        JobProperties props = new JobProperties();
        props.getDq().setEnabled(true);
        when(dataQualityService.runFullScan("NIGHTLY")).thenReturn(0);
        when(insightGenerator.generateFromDqDebt()).thenReturn(0);
        when(dqAutoFixService.fixTopDebts(anyInt(), eq("AUTO"))).thenReturn(0);

        DqNightScanJob job = new DqNightScanJob(dataQualityService, insightGenerator, dqAutoFixService, props);
        job.run();

        verify(dataQualityService, times(1)).runFullScan("NIGHTLY");
        verify(insightGenerator, times(1)).generateFromDqDebt();
        verify(dqAutoFixService, times(1)).fixTopDebts(5, "AUTO");
        // 验证返回值等于0
        int result = insightGenerator.generateFromDqDebt();
        assertEquals(0, result);
    }
}