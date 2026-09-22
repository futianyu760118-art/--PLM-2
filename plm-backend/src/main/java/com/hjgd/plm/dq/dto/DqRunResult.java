package com.hjgd.plm.dq.dto;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class DqRunResult {
    private String objectType;
    private String objectId;
    private int score;
    private int blockCount;
    private int warnCount;
    private int infoCount;
    @Builder.Default
    private List<Item> items = new ArrayList<>();

    public boolean hasBlock() {
        return blockCount > 0;
    }

    @Data
    @Builder
    public static class Item {
        private String ruleCode;
        private String severity;
        private String result;
        private String message;
    }
}
