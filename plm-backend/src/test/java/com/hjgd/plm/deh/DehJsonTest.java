package com.hjgd.plm.deh;

import com.hjgd.plm.deh.service.DehJson;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JSONB 写入规整 —— 防的是「写入成功路径才暴露的 500」：
 * 自然语言字符串直接塞进 JSONB 列会 invalid input syntax for type json。
 */
@DisplayName("DEH JSONB 写入规整")
class DehJsonTest {

    @Test
    @DisplayName("合法 JSON 原样保留，结构不被破坏")
    void keepsValidJson() {
        assertEquals("{\"rule\":\"定义式=实现值\"}", DehJson.value("{\"rule\":\"定义式=实现值\"}"));
        assertEquals("[1,2]", DehJson.refs("[1,2]"));
    }

    @Test
    @DisplayName("纯文本被编码成 JSON 字符串字面量 —— 不再报 invalid input syntax")
    void quotesPlainText() {
        String out = DehJson.value("改 ProjectProgressService 的 CF 计算");
        assertNotNull(out);
        assertTrue(out.startsWith("\"") && out.endsWith("\""), out);
        assertTrue(out.contains("CF"), out);
    }

    @Test
    @DisplayName("数字/布尔等标量也是合法 JSON")
    void acceptsScalars() {
        assertEquals("3", DehJson.value("3"));
        assertEquals("true", DehJson.value("true"));
    }

    @Test
    @DisplayName("空白 → null（value）/ []（refs）：空引用是「没有」，不是「未知」")
    void blankHandling() {
        assertNull(DehJson.value(null));
        assertNull(DehJson.value("   "));
        assertEquals("[]", DehJson.refs(null));
        assertEquals("[]", DehJson.refs("  "));
    }

    @Test
    @DisplayName("证据引用接受逗号分隔，规整成 JSON 数组")
    void refsAcceptsCommaSeparated() {
        assertEquals("[\"E-004\",\"E-005\"]", DehJson.refs("E-004, E-005"));
        assertEquals("[\"E-005\"]", DehJson.refs("E-005"));
    }
}
