package com.hjgd.plm;

import com.hjgd.plm.ledger.controller.LedgerController;
import com.hjgd.plm.ledger.service.impl.LedgerServiceImpl;
import com.hjgd.plm.subscription.service.impl.SubscriptionServiceImpl;
import com.hjgd.plm.subscription.spi.ItemSubscriptionNotifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 全上下文启动测试（PAND-216 D1 防回归）。
 *
 * <p>仓库其余测试都是不装配容器的手工构造 + mock 测试，Bean 图成环时它们全绿也发现不了——
 * PAND-202 的 D1（ledgerServiceImpl → itemSubscriptionNotifier → subscriptionServiceImpl →
 * ledgerServiceImpl）正是这样漏到运行态的。本测试让 Spring 装配完整的 ApplicationContext，
 * 循环依赖会在 refresh 阶段被直接拒绝，从而在构建期就暴露。
 *
 * <p>数据源用 H2 内存库（PostgreSQL 兼容模式）：本测试只验证 Bean 图能否装配，不执行任何业务 SQL，
 * 因此不需要真实 PostgreSQL；启动即扫描的定时任务也一并关掉，避免无表查询噪声。
 */
@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:plm-ctx-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.druid.initial-size=0",
        "spring.datasource.druid.min-idle=0",
        "plm.subscription.schedule-enabled=false"
})
@DisplayName("应用全上下文启动（PAND-216 D1 循环依赖回归）")
class PlmApplicationContextTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("完整 ApplicationContext 装配成功，D1 环形链路上的 Bean 均可解析")
    void contextLoads() {
        assertNotNull(context.getBean(LedgerController.class));
        assertNotNull(context.getBean(LedgerServiceImpl.class));
        assertNotNull(context.getBean(ItemSubscriptionNotifier.class));
        assertNotNull(context.getBean(SubscriptionServiceImpl.class));
    }
}
