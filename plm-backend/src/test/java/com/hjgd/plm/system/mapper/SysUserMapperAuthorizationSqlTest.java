package com.hjgd.plm.system.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PAND-151 / D1（SQL 侧）：鉴权两条语句都必须按未删除角色过滤。
 *
 * <p>无法在单元测试中连真库，故直接对 mapper XML 做守卫断言：
 * sys_role 是逻辑删除表，若少了 {@code r.deleted = 0}，已删角色的授权会继续生效——
 * 这正是 D1 的根因（逻辑删除不触发数据库 ON DELETE CASCADE）。
 */
@DisplayName("SysUserMapper.xml: D1 授权查询须过滤已删除角色")
class SysUserMapperAuthorizationSqlTest {

    private static final String XML_PATH = "mapper/SysUserMapper.xml";
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private String sqlOf(String statementId) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(XML_PATH)) {
            assertNotNull(in, XML_PATH + " 不在 classpath");
            String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Matcher m = Pattern.compile(
                    "<select id=\"" + statementId + "\"[^>]*>(.*?)</select>",
                    Pattern.DOTALL).matcher(xml);
            assertTrue(m.find(), "未找到语句 " + statementId);
            return WHITESPACE.matcher(m.group(1)).replaceAll(" ").trim();
        }
    }

    @Test
    @DisplayName("selectRoleCodesByUserId：关联 sys_role 且过滤 deleted = 0")
    void roleCodesMustFilterDeletedRole() throws IOException {
        String sql = sqlOf("selectRoleCodesByUserId");
        assertTrue(sql.contains("JOIN sys_role r"), "必须关联 sys_role 才能判断是否已删除: " + sql);
        assertTrue(sql.contains("r.deleted = 0"), "缺少已删除角色过滤: " + sql);
        assertTrue(sql.contains("ur.user_id = #{userId}"), sql);
    }

    @Test
    @DisplayName("selectPermissionsByUserId：关联 sys_role 且过滤 deleted = 0")
    void permissionsMustFilterDeletedRole() throws IOException {
        String sql = sqlOf("selectPermissionsByUserId");
        assertTrue(sql.contains("JOIN sys_role r"), "必须关联 sys_role 才能判断是否已删除: " + sql);
        assertTrue(sql.contains("r.deleted = 0"), "缺少已删除角色过滤: " + sql);
        assertTrue(sql.contains("sys_role_permission"), sql);
        assertTrue(sql.contains("ur.user_id = #{userId}"), sql);
    }
}
