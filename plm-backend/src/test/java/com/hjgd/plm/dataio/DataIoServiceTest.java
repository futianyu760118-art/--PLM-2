package com.hjgd.plm.dataio;

import com.alibaba.excel.EasyExcel;
import com.hjgd.plm.dataio.model.ImportResult;
import com.hjgd.plm.dataio.model.SelfCheckResult;
import com.hjgd.plm.dataio.registry.ModuleRegistry;
import com.hjgd.plm.dataio.service.DataIoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * 通用数据导入/导出/自检引擎(注册表驱动)。
 */
@DisplayName("通用数据 IO 引擎")
@ExtendWith(MockitoExtension.class)
class DataIoServiceTest {

    @Mock private JdbcTemplate jdbc;
    private DataIoService service;

    @BeforeEach
    void setUp() {
        service = new DataIoService(jdbc, new ModuleRegistry());
        lenient().when(jdbc.queryForList(anyString())).thenReturn(List.of());
    }

    @Test
    @DisplayName("模块注册表: 覆盖主要业务模块")
    void shouldRegisterModules() {
        assertNotNull(service.def("material"));
        assertNotNull(service.def("initiation"));
        assertNotNull(service.def("project"));
        assertNotNull(service.def("process"));
        assertEquals(11, service.listModules().size());
        assertTrue(service.listModules().stream().anyMatch(m -> "物料主数据".equals(m.get("name"))));
    }

    @Test
    @DisplayName("模板导出: 生成 xlsx 字节流")
    void shouldGenerateTemplate() {
        byte[] body = service.template("material");
        assertTrue(body.length > 500);
        // xlsx 魔数 PK
        assertEquals('P', body[0]);
        assertEquals('K', body[1]);
    }

    @Test
    @DisplayName("导出: 读取数据并写出 xlsx")
    void shouldExportRows() {
        Map<String, Object> row = new HashMap<>();
        row.put("part_no", "HJ001");
        row.put("material_name", "投光灯外壳");
        row.put("status", "RELEASED");
        org.mockito.Mockito.when(jdbc.queryForList(anyString())).thenReturn(List.of(row));

        byte[] body = service.export("material");

        assertTrue(body.length > 500);
    }

    @Test
    @DisplayName("导入预检: 识别表头并校验必填/枚举")
    void shouldValidateOnDryRunImport() throws Exception {
        byte[] xlsx = buildWorkItemXlsx();
        MockMultipartFile file = new MockMultipartFile("file", "wi.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx);

        ImportResult res = service.importExcel("workitem", file, true);

        assertEquals(2, res.getTotal());
        assertEquals(1, res.getSuccess());
        assertEquals(1, res.getFailed());
        assertTrue(res.getMapping().containsValue("item_no"));
        assertTrue(res.getErrors().stream().anyMatch(e -> e.field().equals("title")));
        assertTrue(res.getErrors().stream().anyMatch(e -> e.field().equals("status")));
        assertEquals(0, res.getInserted());
    }

    @Test
    @DisplayName("自检: 完整率/必填/枚举/唯一/日期格式 规则")
    void shouldRunSelfCheck() {
        Map<String, Object> ok = new HashMap<>();
        ok.put("id", 1L); ok.put("item_no", "WI001"); ok.put("title", "正常待办");
        ok.put("status", "OPEN"); ok.put("priority", 2); ok.put("sla_due_at", "2026-09-20");
        Map<String, Object> bad = new HashMap<>();
        bad.put("id", 2L); bad.put("item_no", "WI001"); bad.put("title", "");
        bad.put("status", "BAD"); bad.put("priority", "abc"); bad.put("sla_due_at", "2026-13");
        org.mockito.Mockito.when(jdbc.queryForList(anyString())).thenReturn(List.of(ok, bad));

        SelfCheckResult res = service.selfCheck("workitem");

        assertEquals(2, res.getTotalRows());
        assertTrue(res.getScore() < 100);
        assertTrue(res.getBySeverity().get("HIGH") >= 1, "必填为空 + 唯一重复 应为 HIGH");
        assertTrue(res.getBySeverity().get("MEDIUM") >= 2, "枚举非法 + 非数字/日期异常 应为 MEDIUM");
        assertTrue(res.getIssues().stream().anyMatch(i -> i.message().contains("必填")));
        assertTrue(res.getIssues().stream().anyMatch(i -> i.message().contains("枚举")));
        assertTrue(res.getIssues().stream().anyMatch(i -> i.message().contains("唯一键重复")));
        assertTrue(res.getCompleteness() < 1.0);
    }

    private byte[] buildWorkItemXlsx() {
        List<List<String>> head = List.of(
                List.of("待办编号"), List.of("类型"), List.of("标题"), List.of("关联类型"), List.of("关联ID"),
                List.of("优先级"), List.of("负责人ID"), List.of("状态"), List.of("到期时间"));
        List<List<Object>> data = List.of(
                java.util.Arrays.asList("WI001", "PROCESS", "工序待办", "PROCESS_STEP", "1", "2", "1", "OPEN", "2026-09-20"),
                java.util.Arrays.asList("WI002", "PROCESS", "", "", "", "3", "", "BADSTATUS", "2026-09-21")
        );
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out).head(head).sheet(0).doWrite(data);
        return out.toByteArray();
    }
}
