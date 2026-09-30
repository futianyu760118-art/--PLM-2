package com.hjgd.plm.system.controller;

import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.system.entity.SysRole;
import com.hjgd.plm.system.mapper.SysPermissionMapper;
import com.hjgd.plm.system.mapper.SysRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * PAND-151 / D1：角色是逻辑删除（sys_role.deleted），数据库的 ON DELETE CASCADE 不会触发，
 * 因此必须在应用层物理清理 sys_role_permission / sys_user_role，否则已删角色继续授权。
 */
@DisplayName("RoleController.delete: D1 逻辑删除角色须物理清理关联表")
class RoleControllerDeleteTest {

    private SysRoleMapper roleMapper;
    private SysPermissionMapper permissionMapper;
    private JdbcTemplate jdbcTemplate;
    private RoleController controller;

    @BeforeEach
    void setUp() {
        roleMapper = mock(SysRoleMapper.class);
        permissionMapper = mock(SysPermissionMapper.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        controller = new RoleController(roleMapper, permissionMapper, jdbcTemplate);
    }

    private SysRole role(long id, Integer builtin) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setBuiltin(builtin);
        return role;
    }

    @Test
    @DisplayName("自定义角色：先物理清理两张关联表，再逻辑删除角色")
    void shouldPurgeAssociationsBeforeLogicalDelete() {
        when(roleMapper.selectById(9L)).thenReturn(role(9L, 0));

        Result<Void> result = controller.delete(9L);

        assertTrue(result.isSuccess(), result.getMessage());
        // 顺序即正确性：先断授权，再删角色；反序则中途失败会留下孤儿授权
        InOrder order = inOrder(jdbcTemplate, roleMapper);
        order.verify(jdbcTemplate).update("DELETE FROM sys_role_permission WHERE role_id = ?", 9L);
        order.verify(jdbcTemplate).update("DELETE FROM sys_user_role WHERE role_id = ?", 9L);
        order.verify(roleMapper).deleteById(9L);
    }

    @Test
    @DisplayName("内置角色(builtin=1)：拒绝删除且不触碰任何关联表")
    void shouldRejectBuiltinRole() {
        when(roleMapper.selectById(1L)).thenReturn(role(1L, 1));

        Result<Void> result = controller.delete(1L);

        assertFalse(result.isSuccess());
        verifyNoInteractions(jdbcTemplate);
        verify(roleMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("角色不存在：抛业务异常且不触碰任何关联表")
    void shouldRejectMissingRole() {
        when(roleMapper.selectById(404L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> controller.delete(404L));

        verifyNoInteractions(jdbcTemplate);
        verify(roleMapper, never()).deleteById(anyLong());
    }
}
