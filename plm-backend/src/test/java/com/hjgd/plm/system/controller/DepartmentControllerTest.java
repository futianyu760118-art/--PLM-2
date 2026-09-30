package com.hjgd.plm.system.controller;

import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.system.entity.SysDepartment;
import com.hjgd.plm.system.mapper.SysDepartmentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PAND-151 / D4：补齐部门树维护接口（AC14.1/AC14.3），
 * 并保证删除被引用的部门会被拒绝，避免 sys_user.dept_id 悬空。
 */
@DisplayName("DepartmentController: D4 部门树 / 增删改与删除护栏")
class DepartmentControllerTest {

    private SysDepartmentMapper departmentMapper;
    private JdbcTemplate jdbcTemplate;
    private DepartmentController controller;

    @BeforeEach
    void setUp() {
        departmentMapper = mock(SysDepartmentMapper.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        controller = new DepartmentController(departmentMapper, jdbcTemplate);
    }

    private SysDepartment dept(Long id, String code, String name, Long parentId) {
        SysDepartment d = new SysDepartment();
        d.setId(id);
        d.setDeptCode(code);
        d.setDeptName(name);
        d.setParentId(parentId);
        d.setSortOrder(1);
        d.setStatus(1);
        return d;
    }

    @Test
    @DisplayName("部门树：parentId=0/null 为根，其余挂到父节点下且不回环")
    void shouldBuildTree() {
        when(departmentMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(
                dept(1L, "RD", "研发中心", 0L),
                dept(2L, "RDA", "开发A部", 1L),
                dept(3L, "QA", "品质", null))));

        Result<List<SysDepartment>> result = controller.tree();

        assertTrue(result.isSuccess());
        List<SysDepartment> roots = result.getData();
        assertEquals(2, roots.size(), "应有 2 个根节点: " + roots);
        SysDepartment rd = roots.get(0);
        assertEquals("RD", rd.getDeptCode());
        assertEquals(1, rd.getChildren().size());
        assertEquals("RDA", rd.getChildren().get(0).getDeptCode());
        assertEquals("QA", roots.get(1).getDeptCode());
        assertTrue(roots.get(1).getChildren().isEmpty());
    }

    @Test
    @DisplayName("新增部门：补默认值（status=1、sortOrder=0、parentId=0）后落库")
    void shouldCreateWithDefaults() {
        when(departmentMapper.selectCount(any())).thenReturn(0L);
        SysDepartment payload = new SysDepartment();
        payload.setDeptCode("FIN");
        payload.setDeptName("财务部");
        // 客户端伪造的 id / children 不得带入库
        payload.setId(999L);
        payload.setChildren(new ArrayList<>());

        Result<SysDepartment> result = controller.create(payload);

        assertTrue(result.isSuccess());
        SysDepartment saved = result.getData();
        assertEquals(0L, saved.getParentId());
        assertEquals(1, saved.getStatus());
        assertEquals(0, saved.getSortOrder());
        assertNotNull(saved.getCreatedAt());
        verify(departmentMapper).insert(saved);
    }

    @Test
    @DisplayName("新增部门：编码重复拒绝")
    void shouldRejectDuplicateCode() {
        when(departmentMapper.selectCount(any())).thenReturn(1L);
        SysDepartment payload = new SysDepartment();
        payload.setDeptCode("RD");
        payload.setDeptName("研发中心");

        assertThrows(BusinessException.class, () -> controller.create(payload));
        verify(departmentMapper, never()).insert(any());
    }

    @Test
    @DisplayName("新增部门：上级部门不存在则拒绝")
    void shouldRejectMissingParent() {
        when(departmentMapper.selectCount(any())).thenReturn(0L);
        when(departmentMapper.selectById(77L)).thenReturn(null);
        SysDepartment payload = new SysDepartment();
        payload.setDeptCode("SUB");
        payload.setDeptName("子部门");
        payload.setParentId(77L);

        assertThrows(BusinessException.class, () -> controller.create(payload));
        verify(departmentMapper, never()).insert(any());
    }

    @Test
    @DisplayName("删除部门：存在下级部门则拒绝")
    void shouldRejectDeleteWhenChildrenExist() {
        when(departmentMapper.selectById(1L)).thenReturn(dept(1L, "RD", "研发中心", 0L));
        when(departmentMapper.selectCount(any())).thenReturn(2L);

        BusinessException ex = assertThrows(BusinessException.class, () -> controller.delete(1L));

        assertTrue(ex.getMessage().contains("下级部门"), ex.getMessage());
        verify(departmentMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("删除部门：仍被用户（含逻辑删除用户）引用则拒绝，防止 dept_id 悬空")
    void shouldRejectDeleteWhenReferencedByUser() {
        when(departmentMapper.selectById(4L)).thenReturn(dept(4L, "QA", "品质", 0L));
        when(departmentMapper.selectCount(any())).thenReturn(0L);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> controller.delete(4L));

        assertTrue(ex.getMessage().contains("已被用户引用"), ex.getMessage());
        verify(departmentMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("删除部门：无子部门且无引用时执行物理删除")
    void shouldDeleteWhenUnreferenced() {
        when(departmentMapper.selectById(8L)).thenReturn(dept(8L, "TMP", "临时部门", 0L));
        when(departmentMapper.selectCount(any())).thenReturn(0L);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any())).thenReturn(0L);

        Result<Void> result = controller.delete(8L);

        assertTrue(result.isSuccess());
        verify(departmentMapper).deleteById(8L);
    }
}
