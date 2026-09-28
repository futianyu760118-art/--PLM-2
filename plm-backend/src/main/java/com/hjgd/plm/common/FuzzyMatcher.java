package com.hjgd.plm.common;

/**
 * 表头/字段模糊匹配 (忽略大小写、空格、常见标点; 含中文)。
 * 用于导入时按字段智能匹配列。
 */
public final class FuzzyMatcher {

    private FuzzyMatcher() {}

    /** 归一化: 去空格/标点, 转小写, 仅保留字母数字(CJK 视为字母)。 */
    public static String normalize(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toLowerCase().toCharArray()) {
            if (Character.isLetterOrDigit(c)) sb.append(c);
        }
        return sb.toString();
    }

    /** 匹配得分: 0=不匹配; 越大越匹配。 */
    public static int score(String header, String alias) {
        String h = normalize(header);
        String a = normalize(alias);
        if (h.isEmpty() || a.isEmpty()) return 0;
        if (h.equals(a)) return 100;
        if (h.startsWith(a) || h.endsWith(a)) return 80;
        if (h.contains(a)) return 60;
        if (a.contains(h) && h.length() >= 2) return 40;
        return 0;
    }

    public static boolean match(String header, String alias) {
        return score(header, alias) > 0;
    }
}
