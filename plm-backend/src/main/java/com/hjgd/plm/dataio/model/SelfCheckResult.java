package com.hjgd.plm.dataio.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class SelfCheckResult {
    private String module;
    private String name;
    private String table;
    private int totalRows;
    private int score;              // 健康分 0-100
    private double completeness;    // 字段完整率 0-1
    private Map<String, Integer> bySeverity = new LinkedHashMap<>(); // HIGH/MEDIUM/LOW
    private List<FieldStat> fields = new ArrayList<>();
    private List<Issue> issues = new ArrayList<>();
    private boolean truncated;

    public record FieldStat(String field, String label, boolean required, int filled, int total, double rate) {}

    public record Issue(Long rowId, String keyValue, String field, String label, String severity, String message) {}
}
