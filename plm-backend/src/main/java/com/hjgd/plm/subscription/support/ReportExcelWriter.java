package com.hjgd.plm.subscription.support;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.hjgd.plm.subscription.dto.RepoFileVO;
import com.hjgd.plm.subscription.dto.RepositoryItemVO;
import com.hjgd.plm.subscription.entity.PlmReport;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 报告 Excel 明细（AC-B5「附件：Excel 明细（.xlsx）」、AC-B5-7 按期下载）。
 *
 * <p>不打文件：内容全部来自报告自身的 {@code content_json} 快照，需要时按需生成字节流。
 * 这样「下载到的 Excel」与「当时推送的 Excel」必然一致，也不会留下无人清理的历史文件。
 */
@Component
public class ReportExcelWriter {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public byte[] write(PlmReport report, List<RepositoryItemVO> items) {
        List<ReportExcelRow> rows = new ArrayList<>();
        int index = 1;
        for (RepositoryItemVO item : items) {
            ReportExcelRow row = new ReportExcelRow();
            row.setIndex(index++);
            row.setReportNo(report.getReportNo());
            row.setName(item.getName());
            row.setTypeName(item.getTypeName());
            row.setCategories(String.join("；", item.getCategories()));
            row.setOwnerName(item.getOwnerName());
            row.setLedgerNo(item.getLedgerNo());
            row.setUploadedAt(fmt(item.getUploadedAt()));
            row.setLastModifiedAt(fmt(item.getLastModifiedAt()));
            row.setAttachmentNames(fileNames(item));
            row.setLink(item.getLink());
            rows.add(row);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out, ReportExcelRow.class).sheet("资料明细").doWrite(rows);
        return out.toByteArray();
    }

    private String fileNames(RepositoryItemVO item) {
        if (item.getFiles() == null || item.getFiles().isEmpty()) {
            return "";
        }
        List<String> names = new ArrayList<>();
        for (RepoFileVO file : item.getFiles()) {
            names.add(file.getFileName());
        }
        return String.join("；", names);
    }

    private String fmt(LocalDateTime time) {
        return time == null ? "" : time.format(TS);
    }

    /** Excel 明细行：字段与报告正文逐列对应，便于对照查看 */
    @Data
    @ColumnWidth(18)
    public static class ReportExcelRow {

        @ExcelProperty("序号")
        @ColumnWidth(6)
        private Integer index;

        @ExcelProperty("报告编号")
        @ColumnWidth(22)
        private String reportNo;

        @ExcelProperty("资料名称")
        @ColumnWidth(32)
        private String name;

        @ExcelProperty("资料类型")
        private String typeName;

        @ExcelProperty("分类标签")
        @ColumnWidth(28)
        private String categories;

        @ExcelProperty("责任人")
        private String ownerName;

        @ExcelProperty("来源证据编号")
        @ColumnWidth(22)
        private String ledgerNo;

        @ExcelProperty("上传时间")
        @ColumnWidth(20)
        private String uploadedAt;

        @ExcelProperty("变更时间")
        @ColumnWidth(20)
        private String lastModifiedAt;

        @ExcelProperty("附件")
        @ColumnWidth(30)
        private String attachmentNames;

        @ExcelProperty("资料链接")
        @ColumnWidth(46)
        private String link;
    }
}
