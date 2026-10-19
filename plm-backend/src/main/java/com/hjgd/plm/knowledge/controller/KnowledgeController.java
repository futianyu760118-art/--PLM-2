package com.hjgd.plm.knowledge.controller;

import com.hjgd.plm.agent.service.LlmService;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 知识库（对接腾讯 ima 导出资料）：录入文档 + 关键词检索 + 大模型问答。
 * ima 导出后以标题+正文文本入库，智能体据此回答。
 */
@Tag(name = "V1 知识库")
@RestController
@RequestMapping("/v1/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final JdbcTemplate jdbc;
    private final LlmService llmService;

    @Operation(summary = "知识库列表/检索")
    @GetMapping
    public Result<Map<String, Object>> list(@RequestParam(required = false) String keyword) {
        String where = "WHERE deleted=0";
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(keyword)) {
            where += " AND (title ILIKE ? OR content ILIKE ?)";
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
        }
        List<Map<String, Object>> items = jdbc.queryForList(
                "SELECT id, title, source, created_by, created_at FROM plm_knowledge " + where + " ORDER BY id DESC LIMIT 200", args.toArray());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items);
        return Result.success(out);
    }

    @Operation(summary = "录入知识(标题+正文，供 ima 导出文本粘贴/上传)")
    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        String title = String.valueOf(body.getOrDefault("title", "")).trim();
        String content = String.valueOf(body.getOrDefault("content", "")).trim();
        if (title.isEmpty() || content.isEmpty()) {
            return Result.failed(400, "标题和正文不能为空");
        }
        String source = body.get("source") == null ? "ima" : String.valueOf(body.get("source"));
        String by = SecurityUtils.getCurrentRealName();
        jdbc.update("INSERT INTO plm_knowledge(title, content, source, created_by) VALUES (?,?,?,?)",
                title, content, source, by);
        return Result.success(Map.of("ok", true));
    }

    @Operation(summary = "知识库问答(检索相关段落 + 大模型回答)")
    @PostMapping("/ask")
    public Result<Map<String, Object>> ask(@RequestBody Map<String, Object> body) {
        String q = String.valueOf(body.getOrDefault("question", "")).trim();
        if (q.isEmpty()) return Result.failed(400, "问题不能为空");
        List<Map<String, Object>> hits = jdbc.queryForList(
                "SELECT id, title, content FROM plm_knowledge WHERE deleted=0 AND (title ILIKE ? OR content ILIKE ?) ORDER BY id DESC LIMIT 5",
                "%" + q + "%", "%" + q + "%");
        StringBuilder ctx = new StringBuilder();
        List<Map<String, Object>> refs = new ArrayList<>();
        for (Map<String, Object> h : hits) {
            String c = String.valueOf(h.get("content"));
            ctx.append("【").append(h.get("title")).append("】\n").append(c.length() > 1200 ? c.substring(0, 1200) : c).append("\n\n");
            refs.add(Map.of("id", h.get("id"), "title", h.get("title")));
        }
        String answer;
        if (!llmService.enabled() || ctx.length() == 0) {
            answer = ctx.length() == 0 ? "知识库里暂时没有相关内容。" : "已在知识库中找到相关内容（详见引用）。";
        } else {
            String sys = "你是恒剑PLM知识库助手。仅依据下面提供的知识内容回答用户问题，用中文简洁作答；找不到依据就明说。";
            answer = llmService.chat(sys, "知识内容：\n" + ctx + "\n问题：" + q);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("answer", answer);
        out.put("refs", refs);
        return Result.success(out);
    }

    @Operation(summary = "删除知识")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        jdbc.update("UPDATE plm_knowledge SET deleted=1 WHERE id=?", id);
        return Result.success();
    }
}
