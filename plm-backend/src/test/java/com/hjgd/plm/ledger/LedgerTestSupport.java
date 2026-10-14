package com.hjgd.plm.ledger;

import com.hjgd.plm.auth.security.LoginUser;
import com.hjgd.plm.ledger.config.LedgerProperties;
import com.hjgd.plm.ledger.dto.LedgerRequest;
import com.hjgd.plm.ledger.dto.LinkRequest;
import com.hjgd.plm.ledger.entity.PlmLedger;
import com.hjgd.plm.system.entity.SysUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;

/** 台账测试公共夹具：登录上下文、默认上限、入参构造。 */
final class LedgerTestSupport {

    static final Long USER_ID = 7L;
    static final Long DEPT_ID = 9L;
    static final String REAL_NAME = "张三";

    private LedgerTestSupport() {
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

    /** 默认上限：附件 2 个 / 单文件 1MB / 关联 2 个 / 批量 3 条（便于构造边界） */
    static LedgerProperties props() {
        LedgerProperties p = new LedgerProperties();
        p.setMaxAttachments(2);
        p.setMaxFileSizeMb(1);
        p.setMaxLinks(2);
        p.setBatchLinkMax(3);
        return p;
    }

    static LedgerRequest submitRequest(Long... fileIds) {
        LedgerRequest req = new LedgerRequest();
        req.setName("投光灯检验报告");
        req.setType("INSPECT_REPORT");
        req.setOwnerId(USER_ID);
        req.setOccurDate(LocalDate.now().toString());
        req.setVersion("V1.0");
        req.setDeptId(DEPT_ID);
        req.setStatus("SUBMITTED");
        req.setRemark("首检");
        req.setFileIds(List.of(fileIds));
        req.setLinks(List.of(link("PROJECT", "PJ-001")));
        return req;
    }

    static LinkRequest link(String objectType, String objectId) {
        LinkRequest link = new LinkRequest();
        link.setObjectType(objectType);
        link.setObjectId(objectId);
        return link;
    }

    static PlmLedger ledger(Long id, String status) {
        PlmLedger ledger = new PlmLedger();
        ledger.setId(id);
        ledger.setLedgerNo("EV-20261014-0001");
        ledger.setName("投光灯检验报告");
        ledger.setType("INSPECT_REPORT");
        ledger.setOwnerId(USER_ID);
        ledger.setOccurDate(LocalDate.now());
        ledger.setVersion("V1.0");
        ledger.setDeptId(DEPT_ID);
        ledger.setStatus(status);
        ledger.setUploadedBy(USER_ID);
        ledger.setRevision(0);
        return ledger;
    }
}
