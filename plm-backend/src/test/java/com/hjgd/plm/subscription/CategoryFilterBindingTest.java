package com.hjgd.plm.subscription;

import com.hjgd.plm.ledger.dto.CategoryFilter;
import com.hjgd.plm.ledger.dto.CategoryFilterGroup;
import com.hjgd.plm.ledger.dto.LedgerQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.DataBinder;
import org.springframework.web.bind.ServletRequestParameterPropertyValues;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分类条件的 GET 绑定契约（AC-B6-1 的资料汇总处检索）。
 *
 * <p>前端 axios 把 JS 数组序列化成重复键，Spring MVC 的
 * {@code ServletRequestParameterPropertyValues} 又会把重复键读成索引列表元素，
 * 于是「列表的第几个」写在参数名里（{@code dims[0]}），值本身重复出现。
 * 这是 GET 查询串与 JSON 请求体两条路径最容易走岔的地方：请求体走 Jackson 直接反序列化，
 * 不经过本测试涉及的绑定器，所以两条路径都要单独钉住（JSON 那条见
 * {@link SubscriptionServiceImplTest} 的周期订阅用例）。
 *
 * <p>这里用裸 {@link DataBinder} 而不是 MockMvc：被测的是「参数名 → 对象路径」的绑定规则，
 * 与具体 controller 的映射无关，少一层容器也少一层噪声。
 */
@DisplayName("分类筛选 GET 绑定契约（能力线 B）")
class CategoryFilterBindingTest {

    private LedgerQuery bind(String... keyValues) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            request.addParameter(keyValues[i], keyValues[i + 1]);
        }
        LedgerQuery query = new LedgerQuery();
        DataBinder binder = new DataBinder(query);
        binder.bind(new ServletRequestParameterPropertyValues(request));
        return query;
    }

    @Test
    @DisplayName("AC-B6-1 一个维度两个值：dimId 绑到对象、重复的 valueIds 绑成列表")
    void bindsOneDimensionWithRepeatedValueIds() {
        LedgerQuery query = bind(
                "categoryFilter.dims[0].dimId", "5",
                "categoryFilter.dims[0].valueIds", "12",
                "categoryFilter.dims[0].valueIds", "13");

        assertNotNull(query.getCategoryFilter(), "categoryFilter 为空时绑定器应自动实例化（autoGrowNestedPaths）");
        List<CategoryFilterGroup> dims = query.getCategoryFilter().getDims();
        assertEquals(1, dims.size());
        assertEquals(5L, dims.get(0).getDimId());
        assertEquals(List.of(12L, 13L), dims.get(0).getValueIds(),
                "同维度多值 = 或，两个值都必须留下");
        assertFalse(query.getCategoryFilter().isEmpty());
        assertEquals(1, query.getCategoryFilter().effective().size());
    }

    @Test
    @DisplayName("AC-B6-1 跨维度取与：两个维度各自成组，下标不串位")
    void bindsTwoDimensionsInOrder() {
        LedgerQuery query = bind(
                "categoryFilter.dims[0].dimId", "5",
                "categoryFilter.dims[0].valueIds", "12",
                "categoryFilter.dims[1].dimId", "6",
                "categoryFilter.dims[1].valueIds", "21",
                "categoryFilter.dims[1].valueIds", "22");

        List<CategoryFilterGroup> dims = query.getCategoryFilter().getDims();
        assertEquals(2, dims.size());
        assertEquals(5L, dims.get(0).getDimId());
        assertEquals(List.of(12L), dims.get(0).getValueIds());
        assertEquals(6L, dims.get(1).getDimId());
        assertEquals(List.of(21L, 22L), dims.get(1).getValueIds());
        assertEquals(2, query.getCategoryFilter().effective().size(), "两个维度都有效，才能拼出两个 EXISTS");
    }

    @Test
    @DisplayName("不带分类参数时 categoryFilter 保持为空，SQL 不拼分类条件（老调用方兼容）")
    void absentFilterStaysEmpty() {
        LedgerQuery query = bind("keyword", "投光灯", "type", "INSPECT_REPORT");

        assertEquals("投光灯", query.getKeyword());
        assertTrue(query.getCategoryFilter() == null || query.getCategoryFilter().isEmpty(),
                "未选分类时不应产生任何分类收窄");
    }

    @Test
    @DisplayName("值全空的维度被过滤掉：前端留下空下拉框也不能把结果集清空")
    void emptyDimensionIsIgnoredByEffective() {
        CategoryFilter filter = new CategoryFilter();
        CategoryFilterGroup blank = new CategoryFilterGroup();
        blank.setDimId(null);
        blank.setValueIds(List.of());
        CategoryFilterGroup real = new CategoryFilterGroup();
        real.setDimId(6L);
        real.setValueIds(List.of(21L));
        filter.setDims(List.of(blank, real));

        assertFalse(filter.isEmpty());
        assertEquals(List.of(real), filter.effective());
    }
}
