package com.hjgd.plm.log.aspect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PAND-117 / R3 / AC11.2：操作日志参数落库前必须脱敏，口令类字段绝不出现明文。
 */
@DisplayName("OperationLogAspect: 敏感参数脱敏")
class OperationLogAspectMaskTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final OperationLogAspect aspect = new OperationLogAspect(null, mapper);

    private String masked(String json) throws Exception {
        JsonNode node = mapper.readTree(json);
        aspect.maskSensitive(node);
        return mapper.writeValueAsString(node);
    }

    @Test
    @DisplayName("password / oldPassword / newPassword 一律替换为 ******")
    void shouldMaskPasswordFields() throws Exception {
        String out = masked("{\"username\":\"u1\",\"password\":\"PlainSecret\","
                + "\"oldPassword\":\"OldPlain\",\"newPassword\":\"NewPlain\"}");
        assertFalse(out.contains("PlainSecret"), out);
        assertFalse(out.contains("OldPlain"), out);
        assertFalse(out.contains("NewPlain"), out);
        assertTrue(out.contains("******"), out);
        assertTrue(out.contains("\"username\":\"u1\""), "非敏感字段应保留: " + out);
    }

    @Test
    @DisplayName("secret / token / credential 同样脱敏（大小写不敏感）")
    void shouldMaskOtherSensitiveNames() throws Exception {
        String out = masked("{\"clientSecret\":\"s3cret\",\"accessToken\":\"tok123\","
                + "\"CREDENTIAL\":\"cred\",\"normal\":\"keep\"}");
        assertFalse(out.contains("s3cret"), out);
        assertFalse(out.contains("tok123"), out);
        assertFalse(out.contains("cred"), out);
        assertTrue(out.contains("keep"), out);
    }

    @Test
    @DisplayName("嵌套对象与数组内的敏感字段也要脱敏")
    void shouldMaskNestedStructures() throws Exception {
        String out = masked("{\"outer\":{\"inner\":{\"password\":\"DeepPlain\"}},"
                + "\"list\":[{\"password\":\"InArray\"},{\"safe\":\"ok\"}]}");
        assertFalse(out.contains("DeepPlain"), out);
        assertFalse(out.contains("InArray"), out);
        assertTrue(out.contains("ok"), out);
    }

    @Test
    @DisplayName("口令字段为空值时也写掩码，不泄露「是否为空」")
    void shouldMaskEvenWhenValueNull() throws Exception {
        String out = masked("{\"password\":null}");
        assertTrue(out.contains("******"), out);
    }

    @Test
    @DisplayName("isSensitiveName 判定规则")
    void shouldDetectSensitiveNames() {
        assertTrue(aspect.isSensitiveName("password"));
        assertTrue(aspect.isSensitiveName("Password"));
        assertTrue(aspect.isSensitiveName("userPassword"));
        assertTrue(aspect.isSensitiveName("apiSecret"));
        assertTrue(aspect.isSensitiveName("refreshToken"));
        assertTrue(aspect.isSensitiveName("credential"));
        assertFalse(aspect.isSensitiveName("username"));
        assertFalse(aspect.isSensitiveName("partNo"));
        assertFalse(aspect.isSensitiveName(null));
    }

    @Test
    @DisplayName("无敏感字段的载荷原样保留")
    void shouldKeepCleanPayload() throws Exception {
        String json = "{\"partNo\":\"PN-001\",\"qty\":5}";
        ObjectNode node = (ObjectNode) mapper.readTree(json);
        aspect.maskSensitive(node);
        assertEquals("PN-001", node.get("partNo").asText());
        assertEquals(5, node.get("qty").asInt());
    }
}
