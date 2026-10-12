package com.hjgd.plm.deh.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjgd.plm.common.ApiErrorCodes;
import com.hjgd.plm.common.BusinessException;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * DEH 的 JSONB 列写入规整。
 *
 * <p>DEH 把 {@code evidence_refs} / {@code expected_result} / {@code verification_rule}
 * 这类字段落成 JSONB（与 {@code plm_domain_event}、{@code plm_gate_instance} 的写法一致），
 * 但接口收的是自然语言字符串。若原样塞进 JSONB 列，PostgreSQL 会以
 * {@code invalid input syntax for type json} 报 500 —— 而且只在写入成功路径上才暴露。
 * 这里统一在写库前规整：
 * <ul>
 *   <li>{@link #value}：合法 JSON 原样保留，否则编码成 JSON 字符串字面量（纯文本也能存）</li>
 *   <li>{@link #refs}：证据引用统一成 JSON 数组，接受 JSON 数组 / 逗号分隔 / 单个 id</li>
 * </ul>
 */
public final class DehJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DehJson() {
    }

    /** 规整成可落 JSONB 的 JSON 值；空白返回 {@code null}（保持列 NULL）。 */
    public static String value(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        String t = raw.trim();
        if (isJson(t)) return t;
        return quote(t);
    }

    /** 规整成 JSON 数组；空白返回 {@code []}（空引用是「没有」，不是「未知」）。 */
    public static String refs(String raw) {
        if (!StringUtils.hasText(raw)) return "[]";
        String t = raw.trim();
        if (t.startsWith("[")) return t;
        List<String> ids = Arrays.stream(t.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        return write(ids);
    }

    /** 是否已是合法 JSON（对象/数组/数字/布尔/null/带引号字符串都算）。 */
    private static boolean isJson(String s) {
        try {
            MAPPER.readTree(s);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    private static String quote(String s) {
        return write(s);
    }

    private static String write(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new BusinessException(422, "[" + ApiErrorCodes.DECISION_INVALID
                    + "] 内容无法序列化为 JSON: " + o);
        }
    }
}
