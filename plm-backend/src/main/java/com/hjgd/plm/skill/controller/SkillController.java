package com.hjgd.plm.skill.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjgd.plm.agent.service.LlmService;
import com.hjgd.plm.auth.security.SecurityUtils;
import com.hjgd.plm.common.PageResult;
import com.hjgd.plm.common.Result;
import com.hjgd.plm.skill.entity.Skill;
import com.hjgd.plm.skill.mapper.SkillMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "V1 技能库")
@RestController
@RequestMapping("/v1/skills")
@RequiredArgsConstructor
public class SkillController {

    private final SkillMapper skillMapper;
    private final LlmService llmService;

    @Operation(summary = "技能列表(按名称/标签/内容搜索)")
    @GetMapping
    public Result<PageResult<Skill>> page(@RequestParam(defaultValue = "1") int pageNum,
                                          @RequestParam(defaultValue = "20") int pageSize,
                                          @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<Skill> w = new LambdaQueryWrapper<>();
        w.eq(Skill::getDeleted, 0);
        if (StringUtils.hasText(keyword)) {
            w.and(q -> q.like(Skill::getName, keyword).or().like(Skill::getTags, keyword).or().like(Skill::getDescription, keyword));
        }
        w.orderByDesc(Skill::getUpdatedAt);
        return Result.success(PageResult.of(skillMapper.selectPage(new Page<>(pageNum, pageSize), w)));
    }

    @Operation(summary = "技能详情(调用+1)")
    @GetMapping("/{id}")
    public Result<Skill> get(@PathVariable Long id) {
        Skill s = skillMapper.selectById(id);
        if (s == null) return Result.failed(404, "技能不存在");
        s.setUsageCount((s.getUsageCount() == null ? 0 : s.getUsageCount()) + 1);
        skillMapper.updateById(s);
        return Result.success(s);
    }

    @Operation(summary = "新建技能(手工)")
    @PostMapping
    public Result<Skill> create(@RequestBody Skill body) {
        body.setId(null);
        body.setSource("MANUAL");
        body.setUsageCount(0);
        body.setDeleted(0);
        body.setCreatedBy(currentName());
        body.setCreatedAt(LocalDateTime.now());
        body.setUpdatedAt(LocalDateTime.now());
        skillMapper.insert(body);
        return Result.success(body);
    }

    @Operation(summary = "更新技能")
    @PutMapping
    public Result<Skill> update(@RequestBody Skill body) {
        Skill s = skillMapper.selectById(body.getId());
        if (s == null) return Result.failed(404, "技能不存在");
        if (body.getName() != null) s.setName(body.getName());
        if (body.getDescription() != null) s.setDescription(body.getDescription());
        if (body.getContent() != null) s.setContent(body.getContent());
        if (body.getTags() != null) s.setTags(body.getTags());
        s.setUpdatedAt(LocalDateTime.now());
        skillMapper.updateById(s);
        return Result.success(s);
    }

    @Operation(summary = "删除技能")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Skill s = skillMapper.selectById(id);
        if (s != null) { s.setDeleted(1); skillMapper.updateById(s); }
        return Result.success();
    }

    @Data
    public static class AgentGenReq {
        private String description; // 用通俗语言描述任务，智能体总结成技能
    }

    @Operation(summary = "智能体自动生成技能(总结经验)")
    @PostMapping("/agent-generate")
    public Result<Skill> agentGenerate(@RequestBody AgentGenReq req) {
        if (!StringUtils.hasText(req.getDescription())) return Result.failed(400, "请描述要总结的任务");
        if (!llmService.enabled()) return Result.failed(400, "大模型未配置，无法自动生成");
        String sys = "你是技能总结助手。根据用户描述的任务，提炼成一条可复用的技能。只输出JSON，字段: "
                + "{\"name\":\"技能名称(短)\",\"description\":\"一句话说明\",\"content\":\"可复用步骤/经验(分步骤,简洁)\",\"tags\":\"逗号分隔触发关键词\"}。禁止输出JSON以外内容。";
        String out = llmService.chat(sys, req.getDescription());
        String json = null;
        if (out != null) {
            int a = out.indexOf('{');
            int b = out.lastIndexOf('}');
            if (a >= 0 && b > a) json = out.substring(a, b + 1);
        }
        if (json == null) return Result.failed(500, "生成失败，请重试");
        try {
            JsonNode n = new ObjectMapper().readTree(json);
            Skill s = new Skill();
            s.setName(n.path("name").asText("未命名技能"));
            s.setDescription(n.path("description").asText(""));
            s.setContent(n.path("content").asText(""));
            s.setTags(n.path("tags").asText(""));
            s.setSource("AGENT");
            s.setUsageCount(0);
            s.setDeleted(0);
            s.setCreatedBy(currentName());
            s.setCreatedAt(LocalDateTime.now());
            s.setUpdatedAt(LocalDateTime.now());
            skillMapper.insert(s);
            return Result.success(s);
        } catch (Exception e) {
            return Result.failed(500, "生成结果解析失败");
        }
    }

    private String currentName() {
        try { return SecurityUtils.getCurrentRealName(); } catch (Exception e) { return ""; }
    }
}
