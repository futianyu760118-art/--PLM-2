package com.hjgd.plm.subscription.support;

import com.hjgd.plm.subscription.config.SubscriptionProperties;
import com.hjgd.plm.subscription.dto.RepoFileVO;
import com.hjgd.plm.subscription.dto.RepositoryItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 报告 HTML 正文（AC-B5「正文：HTML 清单」）。
 *
 * <p>站内通知正文与邮件正文共用同一份 HTML：两边若各写一遍，同一次推送在两个渠道里
 * 的条目数/链接就会慢慢跑偏。
 *
 * <p>所有取自业务的文本都做转义——资料名称可由用户填写，直接拼进邮件 HTML
 * 会把标记注入到收件人看到的正文里。
 */
@Component
@RequiredArgsConstructor
public class ReportHtmlBuilder {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final SubscriptionProperties properties;

    public String buildPeriod(String subName, LocalDateTime start, LocalDateTime end,
                              List<RepositoryItemVO> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-family:Microsoft YaHei,Arial,sans-serif;font-size:13px;color:#333\">");
        sb.append("<h3 style=\"margin:0 0 6px\">").append(esc(subName)).append("</h3>");
        sb.append("<p style=\"margin:0 0 10px;color:#666\">统计区间：")
                .append(fmt(start)).append(" ~ ").append(fmt(end))
                .append("；共 ").append(items.size()).append(" 条资料</p>");
        if (items.isEmpty()) {
            // AC-B4-5：本期无更新也要给出一份可确认订阅有效的简短报告
            sb.append("<p style=\"padding:10px;background:#f6f6f6;border-radius:4px\">本期无更新。</p>");
            return sb.append("</div>").toString();
        }
        appendTable(sb, items);
        return sb.append("</div>").toString();
    }

    /** 单条订阅的变更报告：先给变更明细，再给该条资料的行，链接规则与周期报告一致（AC-B5-8） */
    public String buildItem(String subName, RepositoryItemVO item, String summary,
                            List<Map<String, Object>> changes) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-family:Microsoft YaHei,Arial,sans-serif;font-size:13px;color:#333\">");
        sb.append("<h3 style=\"margin:0 0 6px\">").append(esc(subName)).append("</h3>");
        sb.append("<p style=\"margin:0 0 10px;color:#666\">").append(esc(summary)).append("</p>");
        if (changes != null && !changes.isEmpty()) {
            sb.append("<table border=\"1\" cellspacing=\"0\" cellpadding=\"4\" ")
                    .append("style=\"border-collapse:collapse;font-size:12px\">")
                    .append("<tr style=\"background:#fafafa\"><th>变更字段</th><th>变更前</th><th>变更后</th>")
                    .append("<th>变更人</th><th>变更时间</th></tr>");
            for (Map<String, Object> change : changes) {
                sb.append("<tr><td>").append(esc(text(change.get("field"))))
                        .append("</td><td>").append(esc(text(change.get("before"))))
                        .append("</td><td>").append(esc(text(change.get("after"))))
                        .append("</td><td>").append(esc(text(change.get("operator"))))
                        .append("</td><td>").append(esc(text(change.get("time"))))
                        .append("</td></tr>");
            }
            sb.append("</table><div style=\"height:10px\"></div>");
        }
        appendTable(sb, List.of(item));
        return sb.append("</div>").toString();
    }

    private void appendTable(StringBuilder sb, List<RepositoryItemVO> items) {
        sb.append("<table border=\"1\" cellspacing=\"0\" cellpadding=\"4\" ")
                .append("style=\"border-collapse:collapse;font-size:12px\">")
                .append("<tr style=\"background:#fafafa\">")
                .append("<th>资料名称</th><th>资料类型</th><th>分类标签</th><th>责任人</th>")
                .append("<th>来源证据编号</th><th>上传时间</th><th>变更时间</th><th>附件</th></tr>");
        for (RepositoryItemVO item : items) {
            sb.append("<tr><td>")
                    // 每一条都带可点击链接，一次点击直达资料汇总处的该条资料（AC-B5-2/B5-4）
                    .append("<a href=\"").append(esc(absolute(item.getLink())))
                    .append("\" style=\"color:#1677ff\">").append(esc(item.getName())).append("</a>")
                    .append("</td><td>").append(esc(item.getTypeName()))
                    .append("</td><td>").append(esc(join(item.getCategories())))
                    .append("</td><td>").append(esc(item.getOwnerName()))
                    .append("</td><td>").append(esc(item.getLedgerNo()))
                    .append("</td><td>").append(fmt(item.getUploadedAt()))
                    .append("</td><td>").append(fmt(item.getLastModifiedAt()))
                    .append("</td><td>").append(esc(fileNames(item)))
                    .append("</td></tr>");
        }
        sb.append("</table>");
    }

    private String absolute(String link) {
        String base = properties.getLinkBaseUrl();
        if (link == null) {
            return "";
        }
        if (base == null || base.isBlank() || link.startsWith("http")) {
            return link;
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) + link : base + link;
    }

    private String fileNames(RepositoryItemVO item) {
        if (item.getFiles() == null || item.getFiles().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (RepoFileVO file : item.getFiles()) {
            if (sb.length() > 0) {
                sb.append("；");
            }
            sb.append(file.getFileName());
        }
        return sb.toString();
    }

    private String join(List<String> list) {
        return list == null ? "" : String.join("；", list);
    }

    private String fmt(LocalDateTime time) {
        return time == null ? "" : time.format(TS);
    }

    private String text(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    /** 最小 HTML 转义：& < > " 四个字符足以阻断标记注入 */
    private String esc(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
