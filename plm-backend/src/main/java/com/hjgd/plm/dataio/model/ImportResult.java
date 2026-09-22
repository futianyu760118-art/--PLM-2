package com.hjgd.plm.dataio.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class ImportResult {
    private String module;
    private String name;
    private boolean dryRun;
    private int total;              // 有效数据行数
    private int success;            // 校验通过行数
    private int failed;             // 校验失败行数
    private int inserted;           // 实际写入行数
    private List<String> headers = new ArrayList<>();
    private Map<String, String> mapping = new LinkedHashMap<>(); // 表头 -> 字段
    private List<ErrorItem> errors = new ArrayList<>();

    public record ErrorItem(int row, String field, String label, String message) {}
}
