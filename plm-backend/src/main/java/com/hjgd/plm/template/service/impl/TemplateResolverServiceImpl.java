package com.hjgd.plm.template.service.impl;

import com.hjgd.plm.archive.constant.ArchiveTreeTemplate;
import com.hjgd.plm.template.service.TemplateResolverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateResolverServiceImpl implements TemplateResolverService {

    private final JdbcTemplate jdbcTemplate;

    private static final String ARCHIVE_TPL_TABLE = "plm_archive_tree_tpl";
    private static final String PARAM_TPL_TABLE = "plm_param_tpl";

    @Override
    public ArchiveTreeTpl resolveArchiveTreeTpl(String category, String productType, String materialType) {
        try {
            List<TplMeta> metas = loadMetas(ARCHIVE_TPL_TABLE);
            String tplCode = pickBest(metas, category, productType, materialType);
            if (tplCode != null) {
                List<TplNode> nodes = loadArchiveNodes(tplCode);
                if (!nodes.isEmpty()) {
                    String name = metas.stream()
                            .filter(m -> tplCode.equals(m.tplCode))
                            .map(m -> m.tplName).findFirst().orElse(tplCode);
                    return new ArchiveTreeTpl(tplCode, name, nodes, false);
                }
            }
        } catch (Exception e) {
            log.warn("resolve archive tree tpl failed, use embedded fallback: {}", e.getMessage());
        }
        return embeddedDefault();
    }

    @Override
    public ParamTpl resolveParamTpl(String category, String productType, String materialType) {
        try {
            List<TplMeta> metas = loadMetas(PARAM_TPL_TABLE);
            String tplCode = pickBest(metas, category, productType, materialType);
            if (tplCode != null) {
                List<ParamItem> items = loadParamItems(tplCode);
                String name = metas.stream()
                        .filter(m -> tplCode.equals(m.tplCode))
                        .map(m -> m.tplName).findFirst().orElse(tplCode);
                return new ParamTpl(tplCode, name, items);
            }
        } catch (Exception e) {
            log.warn("resolve param tpl failed: {}", e.getMessage());
        }
        return new ParamTpl("EMPTY", "无参数模板", Collections.emptyList());
    }

    @Override
    public List<ArchiveTreeTpl> listArchiveTreeTpls() {
        List<TplMeta> metas = loadMetas(ARCHIVE_TPL_TABLE);
        List<ArchiveTreeTpl> list = new ArrayList<>();
        for (TplMeta m : metas) {
            List<TplNode> nodes = loadArchiveNodes(m.tplCode);
            list.add(new ArchiveTreeTpl(m.tplCode, m.tplName, nodes, false));
        }
        return list;
    }

    @Override
    public List<ParamTpl> listParamTpls() {
        List<TplMeta> metas = loadMetas(PARAM_TPL_TABLE);
        List<ParamTpl> list = new ArrayList<>();
        for (TplMeta m : metas) {
            list.add(new ParamTpl(m.tplCode, m.tplName, loadParamItems(m.tplCode)));
        }
        return list;
    }

    @Override
    public List<DictOption> listDictOptions(String dictType) {
        if (dictType == null || dictType.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return jdbcTemplate.query(
                    "SELECT dict_type, dict_label, dict_value, sort_order FROM sys_dict WHERE dict_type=? AND status=1 ORDER BY sort_order",
                    (rs, i) -> new DictOption(
                            rs.getString("dict_type"),
                            rs.getString("dict_label"),
                            rs.getString("dict_value"),
                            rs.getInt("sort_order")),
                    dictType);
        } catch (Exception e) {
            log.warn("load dict {} failed: {}", dictType, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ==================== 解析逻辑(纯函数,可单测) ====================

    static String pickBest(List<TplMeta> metas, String category, String productType, String materialType) {
        return metas.stream()
                .filter(m -> matches(m, category, productType, materialType))
                .max(Comparator.comparingInt(m -> m.priority))
                .map(m -> m.tplCode)
                .orElse(null);
    }

    static boolean matches(TplMeta m, String category, String productType, String materialType) {
        if (isSet(m.matchCategory) && !equalsIgnoreCase(m.matchCategory, category)) {
            return false;
        }
        if (isSet(m.matchProductType) && !equalsIgnoreCase(m.matchProductType, productType)) {
            return false;
        }
        if (isSet(m.matchMaterialType)) {
            if (materialType == null || materialType.isBlank()) {
                return false;
            }
            Set<String> wanted = Arrays.stream(m.matchMaterialType.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .map(String::toUpperCase).collect(Collectors.toSet());
            if (!wanted.contains(materialType.toUpperCase())) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSet(String s) {
        return s != null && !s.isBlank();
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        if (a == null) return b == null;
        return a.equalsIgnoreCase(b);
    }

    // ==================== DB 读取 ====================

    private List<TplMeta> loadMetas(String table) {
        return jdbcTemplate.query(
                "SELECT tpl_code, tpl_name, match_category, match_product_type, match_material_type, priority FROM "
                        + table + " WHERE enabled=1 ORDER BY priority DESC",
                (rs, i) -> {
                    TplMeta m = new TplMeta();
                    m.tplCode = rs.getString("tpl_code");
                    m.tplName = rs.getString("tpl_name");
                    m.matchCategory = rs.getString("match_category");
                    m.matchProductType = rs.getString("match_product_type");
                    m.matchMaterialType = rs.getString("match_material_type");
                    m.priority = rs.getInt("priority");
                    return m;
                });
    }

    private List<TplNode> loadArchiveNodes(String tplCode) {
        return jdbcTemplate.query(
                "SELECT node_code, node_name, parent_code, level_no, sort_order, is_leaf FROM plm_archive_tree_tpl_node WHERE tpl_code=? ORDER BY sort_order",
                (rs, i) -> new TplNode(
                        rs.getString("node_code"),
                        rs.getString("node_name"),
                        rs.getString("parent_code"),
                        rs.getInt("level_no"),
                        rs.getInt("sort_order"),
                        rs.getInt("is_leaf") == 1),
                tplCode);
    }

    private List<ParamItem> loadParamItems(String tplCode) {
        List<ParamItem> items = jdbcTemplate.query(
                "SELECT param_key, param_name, unit, data_type, dict_type, required, dq_severity, sort_order FROM plm_param_tpl_item WHERE tpl_code=? ORDER BY sort_order",
                (rs, i) -> {
                    ParamItem item = new ParamItem(
                            rs.getString("param_key"),
                            rs.getString("param_name"),
                            rs.getString("unit"),
                            rs.getString("data_type"),
                            rs.getString("dict_type"),
                            rs.getInt("required") == 1,
                            rs.getString("dq_severity"),
                            rs.getInt("sort_order"));
                    // ENUM 项内嵌字典选项，建档向导直接渲染下拉
                    if ("ENUM".equalsIgnoreCase(item.dataType) && item.dictType != null && !item.dictType.isBlank()) {
                        item.options = listDictOptions(item.dictType);
                    }
                    return item;
                },
                tplCode);
        return items;
    }

    /** DB 不可用时的兜底：沿用旧硬编码模具树，保证物料创建不中断 */
    private ArchiveTreeTpl embeddedDefault() {
        List<TplNode> nodes = ArchiveTreeTemplate.TEMPLATE.stream()
                .map(n -> new TplNode(n.code, n.name, n.parentCode,
                        n.parentCode == null ? 1 : 2, 0, n.leaf))
                .collect(Collectors.toList());
        return new ArchiveTreeTpl("MOLD_PART(fallback)", "模具件档案树(兜底)", nodes, true);
    }

    static class TplMeta {
        String tplCode;
        String tplName;
        String matchCategory;
        String matchProductType;
        String matchMaterialType;
        int priority;
    }
}
