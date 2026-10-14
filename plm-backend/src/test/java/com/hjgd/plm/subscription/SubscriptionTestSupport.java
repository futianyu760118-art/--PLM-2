package com.hjgd.plm.subscription;

import com.hjgd.plm.auth.security.LoginUser;
import com.hjgd.plm.subscription.config.SubscriptionProperties;
import com.hjgd.plm.subscription.entity.PlmSubscription;
import com.hjgd.plm.system.entity.SysUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalTime;
import java.util.List;

/** 订阅/报告测试公共夹具：登录上下文、默认配置、实体构造。 */
final class SubscriptionTestSupport {

    static final Long USER_ID = 7L;
    static final Long DEPT_ID = 9L;
    static final String REAL_NAME = "张三";

    private SubscriptionTestSupport() {
    }

    /** 建立登录上下文（SecurityUtils 从 SecurityContextHolder 取用户） */
    static void login(String... permissions) {
        SysUser user = new SysUser();
        user.setId(USER_ID);
        user.setUsername("zhangsan");
        user.setRealName(REAL_NAME);
        user.setDeptId(DEPT_ID);
        user.setStatus(1);
        LoginUser loginUser = new LoginUser(user, List.of("ENGINEER"), List.of(permissions));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));
    }

    static void logout() {
        SecurityContextHolder.clearContext();
    }

    /** 默认配置即 PAND-198 的 AC 取值，测试里只在需要造边界时才改 */
    static SubscriptionProperties props() {
        return new SubscriptionProperties();
    }

    static PlmSubscription periodSub(Long id, String period, Integer weekday, Integer monthday) {
        PlmSubscription sub = new PlmSubscription();
        sub.setId(id);
        sub.setSubNo("SUB-20261014-0001");
        sub.setUserId(USER_ID);
        sub.setType("PERIOD");
        sub.setName("每日汇总");
        sub.setPeriod(period);
        sub.setWeekday(weekday);
        sub.setMonthday(monthday);
        sub.setPushTime(LocalTime.of(8, 0));
        sub.setStatus("ENABLED");
        return sub;
    }
}
