package com.hjgd.plm.subscription.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hjgd.plm.auth.security.LoginUser;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.common.ResultCode;
import com.hjgd.plm.ledger.dto.CategoryFilter;
import com.hjgd.plm.ledger.dto.CategoryFilterGroup;
import com.hjgd.plm.ledger.entity.PlmLedger;
import com.hjgd.plm.ledger.mapper.PlmLedgerMapper;
import com.hjgd.plm.ledger.service.LedgerAuditService;
import com.hjgd.plm.ledger.service.LedgerService;
import com.hjgd.plm.subscription.config.SubscriptionProperties;
import com.hjgd.plm.subscription.dto.CategoryDimRequest;
import com.hjgd.plm.subscription.dto.CategoryDimVO;
import com.hjgd.plm.subscription.dto.CategoryValueRequest;
import com.hjgd.plm.subscription.dto.CategoryValueVO;
import com.hjgd.plm.subscription.dto.LedgerCategoryAssignRequest;
import com.hjgd.plm.subscription.entity.PlmCategoryDim;
import com.hjgd.plm.subscription.entity.PlmCategoryValue;
import com.hjgd.plm.subscription.entity.PlmLedgerCategory;
import com.hjgd.plm.subscription.mapper.PlmCategoryDimMapper;
import com.hjgd.plm.subscription.mapper.PlmCategoryValueMapper;
import com.hjgd.plm.subscription.mapper.PlmLedgerCategoryMapper;
import com.hjgd.plm.subscription.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 分类维度实现（B1 / B2）。
 *
 * <p>权限模型：可「改」的范围只由<b>当前登录用户此刻的部门</b>决定（AC-B2-7 跨部门调动即时生效）——
 * 每次请求都按当前 dept_id 现算，不缓存、不在登录时快照部门，因此不存在「调动后要人工调整」的状态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private static final String STATUS_ENABLED = "ENABLED";
    private static final String STATUS_DISABLED = "DISABLED";

    private final PlmCategoryDimMapper dimMapper;
    private final PlmCategoryValueMapper valueMapper;
    private final PlmLedgerCategoryMapper ledgerCategoryMapper;
    private final PlmLedgerMapper ledgerMapper;
    private final LedgerService ledgerService;
    private final LedgerAuditService auditService;
    private final SubscriptionProperties properties;
    private final JdbcTemplate jdbcTemplate;

    // ===================== 查询 =====================

    @Override
    public List<CategoryDimVO> listForSelect() {
        Long deptId = myDeptId();
        List<PlmCategoryDim> dims = dimMapper.selectList(Wrappers.<PlmCategoryDim>lambdaQuery()
                .eq(PlmCategoryDim::getStatus, STATUS_ENABLED)
                .and(w -> w.eq(PlmCategoryDim::getIsGlobal, 1)
                        .or().eq(PlmCategoryDim::getIsShared, 1)
                        .or().eq(PlmCategoryDim::getDeptId, deptId))
                .orderByAsc(PlmCategoryDim::getSortOrder)
                .orderByAsc(PlmCategoryDim::getId));
        List<CategoryDimVO> out = new ArrayList<>();
        for (PlmCategoryDim dim : dims) {
            // 打标签/筛选只需要启用的值；停用值的历史标签由 labelsOf/labelsByLedgerIds 单独取（AC-B1-4）
            out.add(toVO(dim, valuesOf(dim.getId(), true), false));
        }
        return out;
    }

    @Override
    public List<CategoryDimVO> listForManage() {
        Long deptId = myDeptId();
        // 有查看权但无管理权的人（如台账查看人）也能打开本页，此时编辑入口必须收起来：
        // AC-B2-1 的「不显示编辑入口」既指他部门的维度，也指没有管理权的人
        boolean canManage = SecurityUtils.hasPermission("category:manage");
        List<PlmCategoryDim> dims = dimMapper.selectList(Wrappers.<PlmCategoryDim>lambdaQuery()
                .eq(PlmCategoryDim::getDeptId, deptId)
                .orderByAsc(PlmCategoryDim::getSortOrder)
                .orderByAsc(PlmCategoryDim::getId));
        List<CategoryDimVO> out = new ArrayList<>();
        for (PlmCategoryDim dim : dims) {
            CategoryDimVO vo = toVO(dim, valuesOf(dim.getId(), false), canManage);
            vo.setUsedCount(usedCountOfDim(dim.getId()));
            out.add(vo);
        }
        return out;
    }

    // ===================== 维度维护 =====================

    @Override
    @Transactional
    public CategoryDimVO createDim(CategoryDimRequest request) {
        Long deptId = requireDeptId();
        String name = requireText(request.getDimName(), properties.getDimNameMax(), "维度名称");
        if (existsDimName(deptId, name, null)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "维度名称已存在");
        }
        long current = dimMapper.selectCount(Wrappers.<PlmCategoryDim>lambdaQuery()
                .eq(PlmCategoryDim::getDeptId, deptId));
        if (current >= properties.getMaxDimsPerDept()) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "本部门分类维度已达上限 " + properties.getMaxDimsPerDept() + " 个");
        }

        PlmCategoryDim dim = new PlmCategoryDim();
        dim.setDeptId(deptId);
        dim.setDimName(name);
        dim.setIsGlobal(0);
        dim.setIsShared(Boolean.TRUE.equals(request.getShared()) ? 1 : 0);
        dim.setStatus(STATUS_ENABLED);
        dim.setSortOrder(request.getSortOrder() == null ? (int) current : request.getSortOrder());
        dim.setCreatedBy(SecurityUtils.getCurrentUserId());
        dimMapper.insert(dim);

        List<String> values = normalizeNames(request.getValues(), properties.getValueNameMax());
        if (values.size() > properties.getMaxValuesPerDim()) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "单个维度的分类值上限 " + properties.getMaxValuesPerDim() + " 个，当前 " + values.size() + " 个");
        }
        int order = 0;
        for (String v : values) {
            insertValue(dim.getId(), v, order++);
        }
        return toVO(dim, valuesOf(dim.getId(), false), true);
    }

    @Override
    @Transactional
    public CategoryDimVO updateDim(Long dimId, CategoryDimRequest request) {
        PlmCategoryDim dim = requireManageableDim(dimId);
        if (StringUtils.hasText(request.getDimName())) {
            String name = requireText(request.getDimName(), properties.getDimNameMax(), "维度名称");
            if (!name.equals(dim.getDimName()) && existsDimName(dim.getDeptId(), name, dimId)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "维度名称已存在");
            }
            dim.setDimName(name);
        }
        if (request.getShared() != null) {
            dim.setIsShared(request.getShared() ? 1 : 0);
        }
        if (request.getSortOrder() != null) {
            dim.setSortOrder(request.getSortOrder());
        }
        dimMapper.updateById(dim);
        return toVO(dim, valuesOf(dim.getId(), false), true);
    }

    @Override
    public Map<String, Object> dimUsage(Long dimId) {
        requireManageableDim(dimId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dimId", dimId);
        out.put("usedCount", usedCountOfDim(dimId));
        out.put("valueCount", valueMapper.selectCount(Wrappers.<PlmCategoryValue>lambdaQuery()
                .eq(PlmCategoryValue::getDimId, dimId)));
        return out;
    }

    @Override
    @Transactional
    public void deleteDim(Long dimId, boolean confirm) {
        PlmCategoryDim dim = requireManageableDim(dimId);
        long used = usedCountOfDim(dimId);
        if (!confirm) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    used > 0 ? "该维度已被 " + used + " 条资料使用，删除后这些资料的该维度标签将被移除，请确认"
                            : "删除维度不可恢复，请确认");
        }
        // 先摘标签再删维度：AC-B1-7 要求确认后该维度标签从资料上移除
        List<PlmLedgerCategory> tags = ledgerCategoryMapper.selectList(
                Wrappers.<PlmLedgerCategory>lambdaQuery().eq(PlmLedgerCategory::getDimId, dimId));
        for (PlmLedgerCategory tag : tags) {
            ledgerCategoryMapper.deleteById(tag.getId());
        }
        for (PlmCategoryValue value : valueMapper.selectList(
                Wrappers.<PlmCategoryValue>lambdaQuery().eq(PlmCategoryValue::getDimId, dimId))) {
            valueMapper.deleteById(value.getId());
        }
        dimMapper.deleteById(dimId);
        auditService.log("CATEGORY_DIM_DELETE", dim.getDimName(),
                Map.of("dimName", dim.getDimName(), "usedCount", used), null);
    }

    @Override
    @Transactional
    public CategoryValueVO addValue(Long dimId, CategoryValueRequest request) {
        PlmCategoryDim dim = requireManageableDim(dimId);
        String name = requireText(request.getValueName(), properties.getValueNameMax(), "分类值");
        if (existsValueName(dimId, name, null)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "分类值已存在");
        }
        long current = valueMapper.selectCount(Wrappers.<PlmCategoryValue>lambdaQuery()
                .eq(PlmCategoryValue::getDimId, dimId));
        if (current >= properties.getMaxValuesPerDim()) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "维度「" + dim.getDimName() + "」的分类值已达上限 " + properties.getMaxValuesPerDim() + " 个");
        }
        PlmCategoryValue value = new PlmCategoryValue();
        value.setDimId(dimId);
        value.setValueName(name);
        value.setStatus(STATUS_ENABLED);
        value.setSortOrder(request.getSortOrder() == null ? (int) current : request.getSortOrder());
        valueMapper.insert(value);
        return toValueVO(value);
    }

    @Override
    @Transactional
    public CategoryValueVO updateValue(Long dimId, Long valueId, CategoryValueRequest request) {
        requireManageableDim(dimId);
        PlmCategoryValue value = requireValueOfDim(dimId, valueId);
        if (StringUtils.hasText(request.getValueName())) {
            String name = requireText(request.getValueName(), properties.getValueNameMax(), "分类值");
            if (!name.equals(value.getValueName()) && existsValueName(dimId, name, valueId)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "分类值已存在");
            }
            value.setValueName(name);
        }
        if (StringUtils.hasText(request.getStatus())) {
            String status = request.getStatus().toUpperCase();
            if (!STATUS_ENABLED.equals(status) && !STATUS_DISABLED.equals(status)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "非法状态: " + request.getStatus());
            }
            value.setStatus(status);
        }
        if (request.getSortOrder() != null) {
            value.setSortOrder(request.getSortOrder());
        }
        valueMapper.updateById(value);
        return toValueVO(value);
    }

    // ===================== 资料打标签 =====================

    @Override
    @Transactional
    public void assign(Long ledgerId, LedgerCategoryAssignRequest request) {
        PlmLedger ledger = ledgerMapper.selectById(ledgerId);
        if (ledger == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "资料不存在");
        }
        // 标签是对证据的修改，走台账同一套写权限判定，避免越权打标签
        ledgerService.requireWritable(ledgerId);
        List<LedgerCategoryAssignRequest.Item> items =
                request.getItems() == null ? List.of() : request.getItems();
        // 重复维度是纯入参问题（AC-B1-6），先整批校验再落库：
        // 若边写边判，第一条会先插进去，等于把「拒绝」寄托在事务回滚上
        Set<Long> touched = new HashSet<>();
        for (LedgerCategoryAssignRequest.Item item : items) {
            if (item == null || item.getDimId() == null) {
                continue;
            }
            if (!touched.add(item.getDimId())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "同一维度只能写一个分类值");
            }
        }
        Map<String, Object> before = Map.of("categories", labelsOf(ledgerId));
        {
            for (LedgerCategoryAssignRequest.Item item : items) {
                if (item == null || item.getDimId() == null) {
                    continue;
                }
                PlmCategoryDim dim = requireUsableDim(item.getDimId());
                PlmLedgerCategory existing = ledgerCategoryMapper.selectOne(
                        Wrappers.<PlmLedgerCategory>lambdaQuery()
                                .eq(PlmLedgerCategory::getLedgerId, ledgerId)
                                .eq(PlmLedgerCategory::getDimId, dim.getId()));
                if (item.getValueId() == null) {
                    if (existing != null) {
                        ledgerCategoryMapper.deleteById(existing.getId());
                    }
                    continue;
                }
                PlmCategoryValue value = requireValueOfDim(dim.getId(), item.getValueId());
                if (!STATUS_ENABLED.equals(value.getStatus())) {
                    throw new BusinessException(ResultCode.PARAM_ERROR,
                            "分类值「" + value.getValueName() + "」已停用，不能新打标签");
                }
                if (existing == null) {
                    PlmLedgerCategory tag = new PlmLedgerCategory();
                    tag.setLedgerId(ledgerId);
                    tag.setDimId(dim.getId());
                    tag.setValueId(value.getId());
                    ledgerCategoryMapper.insert(tag);
                } else if (!value.getId().equals(existing.getValueId())) {
                    existing.setValueId(value.getId());
                    ledgerCategoryMapper.updateById(existing);
                }
            }
        }
        auditService.log("CATEGORY_ASSIGN", ledger.getLedgerNo(), before,
                Map.of("categories", labelsOf(ledgerId)));
    }

    @Override
    public List<String> labelsOf(Long ledgerId) {
        if (ledgerId == null) {
            return List.of();
        }
        return labelsByLedgerIds(List.of(ledgerId)).getOrDefault(ledgerId, List.of());
    }

    @Override
    public Map<Long, List<String>> labelsByLedgerIds(Collection<Long> ledgerIds) {
        Map<Long, List<String>> out = new HashMap<>();
        if (ledgerIds == null || ledgerIds.isEmpty()) {
            return out;
        }
        List<PlmLedgerCategory> tags = ledgerCategoryMapper.selectList(
                Wrappers.<PlmLedgerCategory>lambdaQuery().in(PlmLedgerCategory::getLedgerId, ledgerIds));
        if (tags.isEmpty()) {
            return out;
        }
        Map<Long, PlmCategoryDim> dims = byId(dimMapper.selectBatchIds(
                tags.stream().map(PlmLedgerCategory::getDimId).collect(Collectors.toSet())));
        Map<Long, PlmCategoryValue> values = byValueId(valueMapper.selectBatchIds(
                tags.stream().map(PlmLedgerCategory::getValueId).collect(Collectors.toSet())));
        for (PlmLedgerCategory tag : tags) {
            PlmCategoryDim dim = dims.get(tag.getDimId());
            PlmCategoryValue value = values.get(tag.getValueId());
            if (dim == null || value == null) {
                continue;
            }
            // 停用值照常回显：历史标签必须还能查到（AC-B1-4）
            out.computeIfAbsent(tag.getLedgerId(), k -> new ArrayList<>())
                    .add(dim.getDimName() + ":" + value.getValueName());
        }
        return out;
    }

    @Override
    public List<Long> visibleDimIds() {
        Long deptId = myDeptId();
        List<PlmCategoryDim> dims = dimMapper.selectList(Wrappers.<PlmCategoryDim>lambdaQuery()
                .and(w -> w.eq(PlmCategoryDim::getIsGlobal, 1)
                        .or().eq(PlmCategoryDim::getIsShared, 1)
                        .or().eq(PlmCategoryDim::getDeptId, deptId)));
        List<Long> ids = new ArrayList<>();
        for (PlmCategoryDim dim : dims) {
            ids.add(dim.getId());
        }
        return ids;
    }

    @Override
    public void validateFilter(CategoryFilter filter) {
        if (filter == null || filter.isEmpty()) {
            return;
        }
        Set<Long> visible = new HashSet<>(visibleDimIds());
        for (CategoryFilterGroup group : filter.effective()) {
            if (!visible.contains(group.getDimId())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "分类维度不可见: " + group.getDimId());
            }
            for (Long valueId : group.getValueIds()) {
                requireValueOfDim(group.getDimId(), valueId);
            }
        }
    }

    @Override
    public String describeFilter(CategoryFilter filter) {
        if (filter == null || filter.isEmpty()) {
            return "本部门全部资料";
        }
        List<String> parts = new ArrayList<>();
        for (CategoryFilterGroup group : filter.effective()) {
            PlmCategoryDim dim = dimMapper.selectById(group.getDimId());
            if (dim == null) {
                continue;
            }
            List<String> names = new ArrayList<>();
            for (PlmCategoryValue value : valueMapper.selectBatchIds(group.getValueIds())) {
                names.add(value.getValueName());
            }
            parts.add(dim.getDimName() + ":" + String.join("/", names));
        }
        return parts.isEmpty() ? "本部门全部资料" : String.join(" 且 ", parts);
    }

    // ===================== 内部 =====================

    private Long myDeptId() {
        LoginUser user = SecurityUtils.getCurrentUser();
        return user.getUser().getDeptId();
    }

    private Long requireDeptId() {
        Long deptId = myDeptId();
        if (deptId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "当前账号未归属部门，无法定义分类维度");
        }
        return deptId;
    }

    /** 可管理 = 本部门自定义的维度；全局维度只读、他部门维度不可见（AC-B2-1/2/6） */
    private PlmCategoryDim requireManageableDim(Long dimId) {
        PlmCategoryDim dim = dimMapper.selectById(dimId);
        if (dim == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "分类维度不存在");
        }
        Long deptId = myDeptId();
        boolean global = dim.getIsGlobal() != null && dim.getIsGlobal() == 1;
        boolean mine = dim.getDeptId() != null && dim.getDeptId().equals(deptId);
        if (global || !mine) {
            deny(global ? "DENIED_CATEGORY_GLOBAL" : "DENIED_CATEGORY_CROSS_DEPT", dim,
                    global ? "全局维度不可修改" : "无权管理他部门的分类维度");
        }
        return dim;
    }

    /** 可用于打标签的维度：本部门 + 共享 + 全局（AC-B2-4/5/6） */
    private PlmCategoryDim requireUsableDim(Long dimId) {
        PlmCategoryDim dim = dimMapper.selectById(dimId);
        if (dim == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "分类维度不存在");
        }
        if (!STATUS_ENABLED.equals(dim.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "分类维度已停用");
        }
        Long deptId = myDeptId();
        boolean visible = (dim.getIsGlobal() != null && dim.getIsGlobal() == 1)
                || (dim.getIsShared() != null && dim.getIsShared() == 1)
                || (dim.getDeptId() != null && dim.getDeptId().equals(deptId));
        if (!visible) {
            deny("DENIED_CATEGORY_USE", dim, "无权使用他部门的非共享分类维度");
        }
        return dim;
    }

    /**
     * 越权拦截统一出口（AC-B2-2/B2-3）：先留痕再抛错，保证「被拒操作 + 目标维度」可追溯。
     * part_no 落维度名称——操作日志表用的是通用字段，台账侧落的是证据编号。
     */
    private void deny(String operation, PlmCategoryDim dim, String reason) {
        auditService.logDenied(operation, dim.getDimName(), reason);
        throw new BusinessException(ResultCode.FORBIDDEN, "无权限");
    }

    private PlmCategoryValue requireValueOfDim(Long dimId, Long valueId) {
        PlmCategoryValue value = valueMapper.selectById(valueId);
        if (value == null || !value.getDimId().equals(dimId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "分类值不属于该维度: " + valueId);
        }
        return value;
    }

    private boolean existsDimName(Long deptId, String name, Long excludeId) {
        LambdaQueryWrapper<PlmCategoryDim> w = Wrappers.<PlmCategoryDim>lambdaQuery()
                .eq(PlmCategoryDim::getDeptId, deptId)
                .eq(PlmCategoryDim::getDimName, name);
        if (excludeId != null) {
            w.ne(PlmCategoryDim::getId, excludeId);
        }
        return dimMapper.selectCount(w) > 0;
    }

    private boolean existsValueName(Long dimId, String name, Long excludeId) {
        LambdaQueryWrapper<PlmCategoryValue> w = Wrappers.<PlmCategoryValue>lambdaQuery()
                .eq(PlmCategoryValue::getDimId, dimId)
                .eq(PlmCategoryValue::getValueName, name);
        if (excludeId != null) {
            w.ne(PlmCategoryValue::getId, excludeId);
        }
        return valueMapper.selectCount(w) > 0;
    }

    private List<PlmCategoryValue> valuesOf(Long dimId, boolean onlyEnabled) {
        LambdaQueryWrapper<PlmCategoryValue> w = Wrappers.<PlmCategoryValue>lambdaQuery()
                .eq(PlmCategoryValue::getDimId, dimId)
                .orderByAsc(PlmCategoryValue::getSortOrder)
                .orderByAsc(PlmCategoryValue::getId);
        if (onlyEnabled) {
            w.eq(PlmCategoryValue::getStatus, STATUS_ENABLED);
        }
        return valueMapper.selectList(w);
    }

    private long usedCountOfDim(Long dimId) {
        List<PlmLedgerCategory> tags = ledgerCategoryMapper.selectList(
                Wrappers.<PlmLedgerCategory>lambdaQuery().eq(PlmLedgerCategory::getDimId, dimId));
        Set<Long> ledgers = new HashSet<>();
        for (PlmLedgerCategory tag : tags) {
            ledgers.add(tag.getLedgerId());
        }
        return ledgers.size();
    }

    private void insertValue(Long dimId, String name, int order) {
        if (existsValueName(dimId, name, null)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "分类值已存在: " + name);
        }
        PlmCategoryValue value = new PlmCategoryValue();
        value.setDimId(dimId);
        value.setValueName(name);
        value.setStatus(STATUS_ENABLED);
        value.setSortOrder(order);
        valueMapper.insert(value);
    }

    private String requireText(String raw, int max, String label) {
        if (!StringUtils.hasText(raw)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, label + "不能为空");
        }
        String text = raw.trim();
        if (text.length() > max) {
            throw new BusinessException(ResultCode.PARAM_ERROR, label + "不能超过 " + max + " 字");
        }
        return text;
    }

    /** 维度值列表：逐项校验长度、去重、去空 */
    private List<String> normalizeNames(List<String> raw, int max) {
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String item : raw) {
            if (!StringUtils.hasText(item)) {
                continue;
            }
            String name = requireText(item, max, "分类值");
            if (seen.add(name)) {
                out.add(name);
            }
        }
        return out;
    }

    private CategoryDimVO toVO(PlmCategoryDim dim, List<PlmCategoryValue> values, boolean editable) {
        CategoryDimVO vo = new CategoryDimVO();
        vo.setId(dim.getId());
        vo.setDimName(dim.getDimName());
        vo.setDeptId(dim.getDeptId());
        vo.setGlobal(dim.getIsGlobal() != null && dim.getIsGlobal() == 1);
        vo.setShared(dim.getIsShared() != null && dim.getIsShared() == 1);
        vo.setStatus(dim.getStatus());
        vo.setSortOrder(dim.getSortOrder());
        vo.setEditable(editable);
        List<CategoryValueVO> valueVOs = new ArrayList<>();
        for (PlmCategoryValue value : values) {
            valueVOs.add(toValueVO(value));
        }
        vo.setValues(valueVOs);
        return vo;
    }

    private CategoryValueVO toValueVO(PlmCategoryValue value) {
        CategoryValueVO vo = new CategoryValueVO();
        vo.setId(value.getId());
        vo.setValueName(value.getValueName());
        vo.setStatus(value.getStatus());
        vo.setSortOrder(value.getSortOrder());
        return vo;
    }

    private Map<Long, PlmCategoryDim> byId(List<PlmCategoryDim> dims) {
        Map<Long, PlmCategoryDim> map = new HashMap<>();
        for (PlmCategoryDim dim : dims) {
            map.put(dim.getId(), dim);
        }
        return map;
    }

    private Map<Long, PlmCategoryValue> byValueId(List<PlmCategoryValue> values) {
        Map<Long, PlmCategoryValue> map = new HashMap<>();
        for (PlmCategoryValue value : values) {
            map.put(value.getId(), value);
        }
        return map;
    }
}
