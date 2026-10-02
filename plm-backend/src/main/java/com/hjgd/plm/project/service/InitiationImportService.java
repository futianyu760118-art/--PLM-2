package com.hjgd.plm.project.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjgd.plm.common.BusinessException;
import com.hjgd.plm.project.entity.ProjectInitiation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 立项申请书 多 Sheet 结构化导入 (复制自 EBMS /api/import/project_initiation_structured)。
 * 主表/基本信息 → 扁平字段; 产品规格对比/可实现性评估/销售预测/特殊要求 → JSON 子表。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InitiationImportService {

    private final ProjectInitiationService initiationService;
    private final com.hjgd.plm.project.mapper.ProjectInitiationMapper initiationMapper;
    private final ObjectMapper json = new ObjectMapper();

    /** 详情字段名 → 实体字段 (含模糊匹配) */
    private static final Map<String, String> FLAT = new LinkedHashMap<>();

    static {
        put("项目编号", "projectNo"); put("编号", "projectNo");
        put("项目名称", "projectName"); put("名称", "projectName");
        put("产品名称", "__name");
        put("项目类型", "projectType"); put("类型", "projectType");
        put("起始时间", "startDate"); put("开始时间", "startDate"); put("立项时间", "startDate");
        put("项目部门", "department"); put("部门", "department"); put("责任单位", "department");
        put("主要负责人", "owner"); put("负责人", "owner");
        put("配合人员", "cooperators"); put("配合", "cooperators");
        put("其他信息", "otherInfo"); put("其他", "otherInfo");
        put("客户编号", "customerNo"); put("客户号", "customerNo");
        put("客户类型", "customerType"); put("客户种类", "customerType");
        put("客户等级", "customerLevel"); put("等级", "customerLevel");
        put("客户赢率", "customerWinRate"); put("赢率", "customerWinRate"); put("中标率", "customerWinRate");
        put("市场状况", "marketStatus"); put("市场", "marketStatus");
        put("客户痛点识别", "customerPain"); put("客户痛点", "customerPain"); put("痛点", "customerPain");
        put("关键成功要素", "keySuccess"); put("成功要素", "keySuccess");
        put("是否有竞争对手", "hasCompetitor"); put("竞争对手", "hasCompetitor"); put("竞品", "hasCompetitor");
        put("客户采购周期", "purchaseCycle"); put("采购周期", "purchaseCycle");
        put("定制开发类型", "devType"); put("定制开发", "devType"); put("定制", "devType");
        put("申请人", "applicant"); put("申请日期", "applyDate");
        put("备注", "remarks");
        // 附加信息统一并入 otherInfo
        put("需求目的", "__other"); put("询价单号", "__other");
        put("竞争对手状态", "__other"); put("资料要求", "__other");
    }

    private static void appendOther(ProjectInitiation r, String v) {
        if (v == null || v.isBlank()) return;
        String cur = r.getOtherInfo();
        r.setOtherInfo(cur == null || cur.isBlank() ? v : cur + "；" + v);
    }

    private static void put(String k, String f) { FLAT.put(k, f); }

    public Map<String, Object> importStructured(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException("请上传文件");
        ProjectInitiation r = new ProjectInitiation();
        int parsed = 0;
        List<String> errors = new ArrayList<>();
        try (InputStream is = file.getInputStream(); Workbook wb = WorkbookFactory.create(is)) {
            Sheet main = findSheet(wb, "立项申请", "主表", "申请书");
            Sheet basic = findSheet(wb, "基本信息");
            Sheet cust = findSheet(wb, "客户信息");
            // 录入源(基本信息/客户信息)先读; 主表最后读(高优先级)。联动版主表为公式(缓存0)会被忽略
            if (basic != null && basic != main) extractFlat(basic, r);
            if (cust != null && cust != main && cust != basic) extractFlat(cust, r);
            extractFlat(main != null ? main : basic, r);

            Sheet spec = findSheet(wb, "产品规格", "规格对比");
            if (spec != null) { String j = parseSpec(spec); if (j != null) r.setProductSpecs(j); parsed++; }
            Sheet feas = findSheet(wb, "可实现", "可行性", "评估");
            if (feas != null) { String j = parseFeas(feas); if (j != null) r.setFeasibility(j); parsed++; }
            Sheet fore = findSheet(wb, "销售预测", "预测");
            if (fore != null) { String j = parseForecast(fore); if (j != null) r.setSalesForecast(j); parsed++; }
            Sheet reqs = findSheet(wb, "特殊要求", "特殊");
            if (reqs != null) { String j = parseReqs(reqs); if (j != null) r.setSpecialReqs(j); parsed++; }
            // 五、立项决议 → approval_signs
            String appr = parseApproval(main);
            if (appr != null) r.setApprovalSigns(appr);

            if (!org.springframework.util.StringUtils.hasText(r.getProjectName()) && r.getProductSpecs() != null) {
                String nm = firstProductName(r.getProductSpecs());
                if (org.springframework.util.StringUtils.hasText(nm)) r.setProjectName(nm);
            }
            if (!org.springframework.util.StringUtils.hasText(r.getProjectName())
                    && org.springframework.util.StringUtils.hasText(r.getProjectNo())) {
                r.setProjectName(r.getProjectNo()); // 回退: 无项目名称时用料号
            }
            if (!org.springframework.util.StringUtils.hasText(r.getProjectName())
                    && !org.springframework.util.StringUtils.hasText(r.getProjectNo())) {
                throw new BusinessException("未识别到立项信息(需含 项目名称/项目编号)");
            }
            if (!org.springframework.util.StringUtils.hasText(r.getApprovalStatus())) r.setApprovalStatus("draft");
            if (!org.springframework.util.StringUtils.hasText(r.getWorkflowStage())) r.setWorkflowStage("apply");
            // 按项目编号去重: 已存在则更新, 否则新建 (与 EBMS 一致)
            ProjectInitiation created;
            ProjectInitiation existing = null;
            if (org.springframework.util.StringUtils.hasText(r.getProjectNo())) {
                existing = initiationMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProjectInitiation>()
                        .eq(ProjectInitiation::getProjectNo, r.getProjectNo()).last("LIMIT 1"));
            }
            boolean updated = false;
            if (existing != null) {
                try {
                    r.setId(existing.getId());
                    created = initiationService.update(r);
                    updated = true;
                } catch (Exception e) {
                    created = initiationService.create(r);
                }
            } else {
                created = initiationService.create(r);
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("success", 1);
            out.put("skipped", 0);
            out.put("errors", errors);
            out.put("parsed", parsed);
            out.put("id", created.getId());
            out.put("initNo", created.getInitNo());
            out.put("updated", updated);
            return out;
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException("解析失败: " + e.getMessage());
        }
    }

    // ---------- 解析 ----------
    private Sheet findSheet(Workbook wb, String... keys) {
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            String n = wb.getSheetName(i);
            for (String k : keys) if (n != null && n.contains(k)) return wb.getSheetAt(i);
        }
        return null;
    }

    private void extractFlat(Sheet sheet, ProjectInitiation r) {
        if (sheet == null) return;
        for (Row row : sheet) {
            String a = cellStr(row.getCell(0));
            String b = cellStr(row.getCell(1));
            if (a.isEmpty()) continue;
            // 需求目的常写在同一列(整行文字)
            if (b.isEmpty() && a.contains("需求目的")) { appendOther(r, a.replaceFirst("^▶\\s*", "").trim()); continue; }
            // 忽略空值及公式缓存产生的 0 / 0.0 (联动版公式列)
            if (b.isEmpty() || "0".equals(b) || "0.0".equals(b) || a.equals(b)) continue;
            String key = a.replaceFirst("^\\d+\\.\\s*", "").replaceFirst("[:：]\\s*$", "").trim();
            String field = FLAT.get(key);
            if (field == null) {
                for (Map.Entry<String, String> e : FLAT.entrySet()) {
                    if (key.contains(e.getKey()) || e.getKey().contains(key)) { field = e.getValue(); break; }
                }
            }
            if ("__other".equals(field)) { appendOther(r, key + "：" + b); continue; }
            if (field != null) applyFlat(r, field, b);
        }
    }

    private void applyFlat(ProjectInitiation r, String field, String v) {
        switch (field) {
            case "projectNo" -> r.setProjectNo(v);
            case "projectName" -> r.setProjectName(v);
            case "projectType" -> r.setProjectType(v);
            case "startDate" -> r.setStartDate(v);
            case "department" -> r.setDepartment(v);
            case "owner" -> r.setOwner(v);
            case "cooperators" -> r.setCooperators(v);
            case "otherInfo" -> r.setOtherInfo(v);
            case "customerNo" -> r.setCustomerNo(v);
            case "customerType" -> r.setCustomerType(v);
            case "customerLevel" -> r.setCustomerLevel(v);
            case "customerWinRate" -> r.setCustomerWinRate(v);
            case "marketStatus" -> r.setMarketStatus(v);
            case "customerPain" -> r.setCustomerPain(v);
            case "keySuccess" -> r.setKeySuccess(v);
            case "hasCompetitor" -> r.setHasCompetitor(v);
            case "purchaseCycle" -> r.setPurchaseCycle(v);
            case "devType" -> r.setDevType(v);
            case "applicant" -> r.setApplicant(v);
            case "applyDate" -> r.setApplyDate(v);
            case "remarks" -> r.setRemarks(v);
            case "__name" -> { if (!org.springframework.util.StringUtils.hasText(r.getProjectName())) r.setProjectName(v); }
            default -> { }
        }
    }

    private String parseSpec(Sheet sheet) throws Exception {
        List<Row> rows = rows(sheet);
        if (rows.size() < 2) return null;
        Row header = rows.get(1);
        List<String> cols = new ArrayList<>();
        for (int i = 1; i < header.getLastCellNum(); i++) {
            String c = cellStr(header.getCell(i));
            if (!c.isEmpty()) cols.add(c);
        }
        List<Map<String, Object>> specs = new ArrayList<>();
        for (int i = 2; i < rows.size(); i++) {
            Row row = rows.get(i);
            String item = cellStr(row.getCell(0));
            if (item.isEmpty() || "规格项".equals(item)) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("规格项", item);
            for (int j = 0; j < cols.size(); j++) {
                String v = cellStr(row.getCell(1 + j));
                if (!v.isEmpty()) m.put(cols.get(j), v);
            }
            specs.add(m);
        }
        return specs.isEmpty() ? null : json.writeValueAsString(specs);
    }

    private String parseFeas(Sheet sheet) throws Exception {
        List<Map<String, Object>> out = new ArrayList<>();
        String cat = "";
        for (Row row : sheet) {
            String a = cellStr(row.getCell(0)), b = cellStr(row.getCell(1));
            if (a.contains("评估大") || "类别".equals(a)) continue;      // 表头
            if (a.contains("可实现") && b.isEmpty()) continue;          // 标题
            if (b.isEmpty()) continue;
            if (!a.isEmpty()) cat = a;                                   // 合并单元格: 继承上一个类别
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("类别", cat); m.put("评估项", b);
            m.put("结果", cellStr(row.getCell(2)));
            m.put("关联项", cellStr(row.getCell(3)));
            m.put("备注", cellStr(row.getCell(4)));
            out.add(m);
        }
        return out.isEmpty() ? null : json.writeValueAsString(out);
    }

    /** 五、立项决议 → approval_signs JSON */
    private String parseApproval(Sheet sheet) throws Exception {
        if (sheet == null) return null;
        List<List<String>> grid = new ArrayList<>();
        for (Row row : sheet) {
            List<String> line = new ArrayList<>();
            for (int i = 0; i < 6; i++) line.add(cellStr(row.getCell(i)));
            grid.add(line);
        }
        int start = -1;
        for (int i = 0; i < grid.size(); i++) if (grid.get(i).get(0).contains("立项人员")) { start = i; break; }
        if (start < 0) return null;
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = start; i < grid.size(); i++) {
            List<String> line = grid.get(i);
            // 遇到下一章节(六、销售预测 等)停止
            if (i > start && !line.get(0).isEmpty() && line.get(0).matches("^[六七八九十]+、.*")) break;
            if (line.stream().allMatch(String::isEmpty)) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("项目", line.get(0));
            List<String> vals = new ArrayList<>();
            for (int j = 1; j < line.size(); j++) if (!line.get(j).isEmpty()) vals.add(line.get(j));
            m.put("值", vals);
            out.add(m);
        }
        return out.isEmpty() ? null : json.writeValueAsString(out);
    }

    private String parseForecast(Sheet sheet) throws Exception {
        List<Row> rows = rows(sheet);
        int hi = -1;
        for (int i = 0; i < rows.size(); i++) {
            if ("时间周期".equals(cellStr(rows.get(i).getCell(0)))) hi = i;
        }
        if (hi < 0) return null;
        Row header = rows.get(hi);
        List<String> cols = new ArrayList<>();
        for (int i = 1; i < header.getLastCellNum(); i++) {
            String c = cellStr(header.getCell(i));
            if (!c.isEmpty()) cols.add(c);
        }
        List<Map<String, Object>> frows = new ArrayList<>();
        for (int i = hi + 1; i < rows.size(); i++) {
            Row row = rows.get(i);
            String period = cellStr(row.getCell(0));
            if (period.isEmpty() || !period.matches(".*(月|年|周|季).*")) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("周期", period);
            for (int j = 0; j < cols.size(); j++) {
                String v = cellStr(row.getCell(1 + j));
                m.put(cols.get(j), v.isEmpty() ? 0 : (v.matches("-?\\d+(\\.\\d+)?") ? Double.valueOf(v) : v));
            }
            frows.add(m);
        }
        // 金额汇总(合计销售数量/单价/小计价格/合计销售金额)
        List<Map<String, Object>> extra = new ArrayList<>();
        for (int i = hi + 1; i < rows.size(); i++) {
            String label = cellStr(rows.get(i).getCell(0));
            if (label.isEmpty() || label.matches(".*(月|年|周|季).*")) continue;
            List<String> vals = new ArrayList<>();
            for (int j = 1; j < 6; j++) {
                String v = cellStr(rows.get(i).getCell(j));
                if (!v.isEmpty()) vals.add(v);
            }
            if (vals.isEmpty()) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("项目", label); m.put("值", vals);
            extra.add(m);
        }
        if (frows.isEmpty() && extra.isEmpty()) return null;
        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("cols", cols);
        obj.put("rows", frows);
        obj.put("extra", extra);
        return json.writeValueAsString(obj);
    }

    /** 取产品规格对比首个产品列名作为项目名称回退 */
    @SuppressWarnings("unchecked")
    private String firstProductName(String specsJson) {
        try {
            List<Map<String, Object>> list = json.readValue(specsJson, List.class);
            if (!list.isEmpty()) {
                for (String k : list.get(0).keySet()) if (!"规格项".equals(k)) return k;
            }
        } catch (Exception ignore) { }
        return null;
    }

    private String parseReqs(Sheet sheet) throws Exception {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Row row : sheet) {
            String a = cellStr(row.getCell(0)), b = cellStr(row.getCell(1));
            if (a.isEmpty() || b.isEmpty()) continue;
            if (a.contains("各产品") || "特殊要求".equals(a)) continue;
            if ("产品".equals(a) || "要求".equals(b)) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("产品", a); m.put("要求", b);
            out.add(m);
        }
        return out.isEmpty() ? null : json.writeValueAsString(out);
    }

    private List<Row> rows(Sheet sheet) {
        List<Row> list = new ArrayList<>();
        for (Row r : sheet) list.add(r);
        return list;
    }

    private String cellStr(Cell c) {
        if (c == null) return "";
        try {
            switch (c.getCellType()) {
                case STRING: return c.getStringCellValue().trim();
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(c))
                        return new SimpleDateFormat("yyyy-MM-dd").format(c.getDateCellValue());
                    double d = c.getNumericCellValue();
                    long l = (long) d;
                    return d == l ? String.valueOf(l) : String.valueOf(d);
                case BOOLEAN: return String.valueOf(c.getBooleanCellValue());
                case FORMULA:
                    try { return c.getStringCellValue().trim(); } catch (Exception e) { return String.valueOf(c.getNumericCellValue()); }
                default: return "";
            }
        } catch (Exception e) { return ""; }
    }

    // ---------- 模板 / 导出 (与「立项申请书_合并版.xlsx」同版式) ----------
    public byte[] template() { return buildWorkbook(null); }

    public byte[] exportStructured(Long id) { return buildWorkbook(initiationService.getById(id)); }

    private byte[] buildWorkbook(ProjectInitiation r) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            buildMain(wb, r);
            buildBasic(wb, r);
            buildSpec(wb, r);
            buildFeas(wb, r);
            buildForecast(wb, r);
            buildReqs(wb, r);
            wb.write(os);
            return os.toByteArray();
        } catch (Exception e) {
            throw new BusinessException("生成文件失败: " + e.getMessage());
        }
    }

    private void cell(Sheet s, int r, int c, Object v) {
        Row row = s.getRow(r);
        if (row == null) row = s.createRow(r);
        Cell cl = row.getCell(c);
        if (cl == null) cl = row.createCell(c);
        if (v == null) cl.setCellValue("");
        else if (v instanceof Number n) cl.setCellValue(n.doubleValue());
        else cl.setCellValue(String.valueOf(v));
    }

    private String g(ProjectInitiation r, java.util.function.Function<ProjectInitiation, String> f) {
        return r == null ? "" : (f.apply(r) == null ? "" : f.apply(r));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseList(String j) {
        if (j == null || j.isBlank()) return new ArrayList<>();
        try { return json.readValue(j, List.class); } catch (Exception e) { return new ArrayList<>(); }
    }

    private List<String> productCols(List<Map<String, Object>> specs) {
        if (specs.isEmpty()) return new ArrayList<>();
        List<String> cols = new ArrayList<>();
        for (String k : specs.get(0).keySet()) if (!"规格项".equals(k)) cols.add(k);
        return cols;
    }

    private void buildMain(XSSFWorkbook wb, ProjectInitiation r) {
        Sheet s = wb.createSheet("立项申请书");
        int R = 0;
        cell(s, R++, 0, "销售需求立项申请书    2025.11版");
        R++;
        cell(s, R++, 0, "一、基本信息（项目经理填写）");
        R = kv(s, R, "1. 项目编号", g(r, ProjectInitiation::getProjectNo));
        R = kv(s, R, "2. 项目类型", g(r, ProjectInitiation::getProjectType));
        R = kv(s, R, "3. 起始时间", g(r, ProjectInitiation::getStartDate));
        R = kv(s, R, "4. 项目部门", g(r, ProjectInitiation::getDepartment));
        R = kv(s, R, "5. 项目主要负责人", g(r, ProjectInitiation::getOwner));
        R = kv(s, R, "6. 配合人员", g(r, ProjectInitiation::getCooperators));
        R = kv(s, R, "7. 其他", g(r, ProjectInitiation::getOtherInfo));
        R++;
        cell(s, R++, 0, "二、立项目和类型（销售输出填写）");
        cell(s, R++, 0, "▶ 客户信息");
        R = kv(s, R, "1. 客户编号", g(r, ProjectInitiation::getCustomerNo));
        R = kv(s, R, "2. 客户类型", g(r, ProjectInitiation::getCustomerType));
        R = kv(s, R, "3. 客户等级", g(r, ProjectInitiation::getCustomerLevel));
        R = kv(s, R, "4. 客户赢率", g(r, ProjectInitiation::getCustomerWinRate));
        R = kv(s, R, "5. 市场状况", g(r, ProjectInitiation::getMarketStatus));
        R = kv(s, R, "6. 客户痛点识别", g(r, ProjectInitiation::getCustomerPain));
        R = kv(s, R, "7. 关键成功要素", g(r, ProjectInitiation::getKeySuccess));
        R = kv(s, R, "8. 是否有竞争对手", g(r, ProjectInitiation::getHasCompetitor));
        R = kv(s, R, "10. 客户采购周期", g(r, ProjectInitiation::getPurchaseCycle));
        R = kv(s, R, "11. 定制开发/转化推广", g(r, ProjectInitiation::getDevType));
        R++;
        cell(s, R++, 0, "三、项目信息（销售输出填写）");
        // 产品规格对比(主表区)
        List<Map<String, Object>> specs = parseList(r == null ? null : r.getProductSpecs());
        List<String> cols = productCols(specs);
        int headerRow = R;
        cell(s, headerRow, 0, "规格项");
        for (int j = 0; j < cols.size(); j++) cell(s, headerRow, 1 + j, cols.get(j));
        R++;
        if (specs.isEmpty()) {
            cell(s, R++, 0, "产品型号");
            cell(s, R++, 0, "产品名称");
            cell(s, R++, 0, "功率");
        } else {
            for (Map<String, Object> sp : specs) {
                cell(s, R, 0, sp.get("规格项"));
                for (int j = 0; j < cols.size(); j++) cell(s, R, 1 + j, sp.get(cols.get(j)));
                R++;
            }
        }
        R++;
        cell(s, R++, 0, "▶ 特殊要求（分产品）");
        List<Map<String, Object>> reqs = parseList(r == null ? null : r.getSpecialReqs());
        for (Map<String, Object> q : reqs) { cell(s, R, 0, q.get("产品")); cell(s, R, 1, q.get("要求")); R++; }
        R++;
        cell(s, R++, 0, "四、可实现性评估（研发填写）");
        int fh = R;
        String[] fhead = {"评估大类", "评估项", "评估结果", "关联项", "备注/数值"};
        for (int j = 0; j < fhead.length; j++) cell(s, fh, j, fhead[j]);
        R++;
        for (Map<String, Object> f : parseList(r == null ? null : r.getFeasibility())) {
            cell(s, R, 0, f.get("类别")); cell(s, R, 1, f.get("评估项")); cell(s, R, 2, f.get("结果"));
            cell(s, R, 3, f.get("关联项")); cell(s, R, 4, f.get("备注")); R++;
        }
        R++;
        cell(s, R++, 0, "五、立项决议");
        List<Map<String, Object>> appr = parseList(r == null ? null : r.getApprovalSigns());
        if (appr.isEmpty()) { cell(s, R, 0, "立项人员"); cell(s, R, 1, "销售总监"); cell(s, R, 2, "研发经理"); cell(s, R, 3, "项目经理"); cell(s, R, 4, "总经理"); R++; }
        else for (Map<String, Object> a : appr) {
            cell(s, R, 0, a.get("项目"));
            List<?> vals = a.get("值") instanceof List<?> l ? l : List.of();
            for (int j = 0; j < vals.size(); j++) cell(s, R, 1 + j, vals.get(j));
            R++;
        }
        R++;
        cell(s, R++, 0, "六、销售预测 & 金额汇总");
        writeForecastRows(s, R, r);
    }

    private int kv(Sheet s, int R, String k, String v) { cell(s, R, 0, k); cell(s, R, 1, v); return R + 1; }

    @SuppressWarnings("unchecked")
    private int writeForecastRows(Sheet s, int R, ProjectInitiation r) {
        Map<String, Object> sf = new LinkedHashMap<>();
        if (r != null && r.getSalesForecast() != null && !r.getSalesForecast().isBlank()) {
            try { sf = json.readValue(r.getSalesForecast(), Map.class); } catch (Exception ignore) {}
        }
        List<String> cols = (List<String>) sf.getOrDefault("cols", new ArrayList<>());
        List<Map<String, Object>> rows = (List<Map<String, Object>>) sf.getOrDefault("rows", new ArrayList<>());
        int h = R;
        cell(s, h, 0, "时间周期");
        for (int j = 0; j < cols.size(); j++) cell(s, h, 1 + j, cols.get(j));
        R++;
        if (rows.isEmpty()) { cell(s, R, 0, "6个月"); R++; cell(s, R, 0, "1年"); R++; }
        else for (Map<String, Object> fr : rows) {
            cell(s, R, 0, fr.get("周期"));
            for (int j = 0; j < cols.size(); j++) cell(s, R, 1 + j, fr.get(cols.get(j)));
            R++;
        }
        List<Map<String, Object>> extra = (List<Map<String, Object>>) sf.getOrDefault("extra", new ArrayList<>());
        for (Map<String, Object> ex : extra) {
            cell(s, R, 0, ex.get("项目"));
            List<?> vals = ex.get("值") instanceof List<?> l ? l : List.of();
            for (int j = 0; j < vals.size(); j++) cell(s, R, 1 + j, vals.get(j));
            R++;
        }
        return R;
    }

    private void buildBasic(XSSFWorkbook wb, ProjectInitiation r) {
        Sheet s = wb.createSheet("子表-基本信息");
        int R = 0;
        cell(s, R++, 0, "基本信息快照");
        R++;
        R = kv(s, R, "项目编号", g(r, ProjectInitiation::getProjectNo));
        R = kv(s, R, "项目类型", g(r, ProjectInitiation::getProjectType));
        R = kv(s, R, "起始时间", g(r, ProjectInitiation::getStartDate));
        R = kv(s, R, "项目部门", g(r, ProjectInitiation::getDepartment));
        R = kv(s, R, "客户编号", g(r, ProjectInitiation::getCustomerNo));
        R = kv(s, R, "客户类型", g(r, ProjectInitiation::getCustomerType));
        R = kv(s, R, "客户等级", g(r, ProjectInitiation::getCustomerLevel));
        R = kv(s, R, "客户赢率", g(r, ProjectInitiation::getCustomerWinRate));
        R = kv(s, R, "市场状况", g(r, ProjectInitiation::getMarketStatus));
        R = kv(s, R, "客户痛点", g(r, ProjectInitiation::getCustomerPain));
        R = kv(s, R, "关键成功要素", g(r, ProjectInitiation::getKeySuccess));
        R = kv(s, R, "竞争对手", g(r, ProjectInitiation::getHasCompetitor));
        R = kv(s, R, "采购周期", g(r, ProjectInitiation::getPurchaseCycle));
        kv(s, R, "定制开发", g(r, ProjectInitiation::getDevType));
    }

    private void buildSpec(XSSFWorkbook wb, ProjectInitiation r) {
        Sheet s = wb.createSheet("子表-产品规格对比");
        int R = 0;
        cell(s, R++, 0, "产品规格对比表");
        List<Map<String, Object>> specs = parseList(r == null ? null : r.getProductSpecs());
        List<String> cols = productCols(specs);
        cell(s, R, 0, "规格项");
        for (int j = 0; j < cols.size(); j++) cell(s, R, 1 + j, cols.get(j));
        R++;
        if (specs.isEmpty()) {
            String[] demo = {"产品型号", "产品名称", "功率", "产品尺寸", "输入电压", "色温", "光通量/光效", "光源", "灯壳", "防水等级", "包装要求", "认证要求", "目标价格(元)", "数量", "合计金额(元)", "样品数量", "询价单号"};
            for (String d : demo) cell(s, R++, 0, d);
        } else for (Map<String, Object> sp : specs) {
            cell(s, R, 0, sp.get("规格项"));
            for (int j = 0; j < cols.size(); j++) cell(s, R, 1 + j, sp.get(cols.get(j)));
            R++;
        }
    }

    private void buildFeas(XSSFWorkbook wb, ProjectInitiation r) {
        Sheet s = wb.createSheet("子表-可实现性评估");
        int R = 0;
        cell(s, R++, 0, "可实现性评估");
        String[] fhead = {"评估大类", "评估项", "评估结果", "关联项", "备注/数值"};
        for (int j = 0; j < fhead.length; j++) cell(s, R, j, fhead[j]);
        R++;
        List<Map<String, Object>> feas = parseList(r == null ? null : r.getFeasibility());
        if (feas.isEmpty()) {
            String[][] demo = {{"设计标准", "是否有设计标准"}, {"测试标准", "是否有测试标准"}, {"测试报告", "是否有现成测试报告模板"}, {"工艺标准", "是否有现成工艺标准"}, {"包装评估", "现成包装是否满足"}, {"竞品评估", "是否有竞品"}, {"硬件评估", "能否实现"}};
            for (String[] d : demo) { cell(s, R, 0, d[0]); cell(s, R, 1, d[1]); R++; }
        } else for (Map<String, Object> f : feas) {
            cell(s, R, 0, f.get("类别")); cell(s, R, 1, f.get("评估项")); cell(s, R, 2, f.get("结果"));
            cell(s, R, 3, f.get("关联项")); cell(s, R, 4, f.get("备注")); R++;
        }
    }

    private void buildForecast(XSSFWorkbook wb, ProjectInitiation r) {
        Sheet s = wb.createSheet("子表-销售预测");
        int R = 0;
        cell(s, R++, 0, "销售预测 & 金额汇总");
        writeForecastRows(s, R, r);
    }

    private void buildReqs(XSSFWorkbook wb, ProjectInitiation r) {
        Sheet s = wb.createSheet("子表-特殊要求");
        int R = 0;
        cell(s, R++, 0, "各产品特殊要求");
        R++;
        List<Map<String, Object>> reqs = parseList(r == null ? null : r.getSpecialReqs());
        if (reqs.isEmpty()) { cell(s, R, 0, "产品特殊要求"); cell(s, R, 1, ""); }
        else for (Map<String, Object> q : reqs) { cell(s, R, 0, q.get("产品")); cell(s, R, 1, q.get("要求")); R++; }
    }
}
