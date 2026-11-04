package com.hjgd.plm.system.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class SequenceService {

    private final JdbcTemplate jdbcTemplate;
    private final ReentrantLock lock = new ReentrantLock();
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    public SequenceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public String nextNo(String seqKey) {
        lock.lock();
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT prefix, date_pattern, length, separator FROM sys_sequence WHERE seq_key = ? FOR UPDATE",
                    seqKey);
            jdbcTemplate.update(
                    "UPDATE sys_sequence SET current_val = current_val + 1, updated_at = now() WHERE seq_key = ?",
                    seqKey);
            Long currentVal = jdbcTemplate.queryForObject(
                    "SELECT current_val FROM sys_sequence WHERE seq_key = ?",
                    Long.class, seqKey);
            String prefix = (String) row.get("prefix");
            Integer length = ((Number) row.get("length")).intValue();
            String separator = (String) row.get("separator");
            String datePart = LocalDateTime.now().format(DATE_FMT);
            String seqPart = String.format("%0" + length + "d", currentVal);
            return prefix + datePart + (separator == null ? "" : separator) + seqPart;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 取纯序号值（不带前缀/日期），供编码规则引擎组装。
     * <p>resetPolicy: NEVER / YEAR / MONTH / DAY —— 进入新期间自动归零重新计数。</p>
     * <p>行锁 FOR UPDATE 保证并发安全，多实例也有效（ReentrantLock 只管单机）。</p>
     */
    @Transactional
    public long nextValue(String seqKey, String resetPolicy) {
        String period = periodOf(resetPolicy);
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT current_val, last_period FROM sys_sequence WHERE seq_key = ? FOR UPDATE", seqKey);
        long cur = row.get("current_val") == null ? 0L : ((Number) row.get("current_val")).longValue();
        Object lastPeriod = row.get("last_period");
        if (period != null && !period.equals(lastPeriod)) {
            cur = 0L;   // 跨期间，归零
        }
        long next = cur + 1;
        jdbcTemplate.update(
                "UPDATE sys_sequence SET current_val = ?, last_period = ?, updated_at = now() WHERE seq_key = ?",
                next, period, seqKey);
        return next;
    }

    /** 预览用：不占号，按当前值 +1 估算（考虑期间重置） */
    public long peekValue(String seqKey, String resetPolicy) {
        String period = periodOf(resetPolicy);
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT current_val, last_period FROM sys_sequence WHERE seq_key = ?", seqKey);
        long cur = row.get("current_val") == null ? 0L : ((Number) row.get("current_val")).longValue();
        Object lastPeriod = row.get("last_period");
        if (period != null && !period.equals(lastPeriod)) {
            return 1L;
        }
        return cur + 1;
    }

    /** resetPolicy -> 期间标识；NEVER 返回 null 表示永不重置 */
    private String periodOf(String resetPolicy) {
        String p = resetPolicy == null ? "NEVER" : resetPolicy.trim().toUpperCase();
        switch (p) {
            case "YEAR":  return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy"));
            case "MONTH": return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
            case "DAY":   return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            default:      return null;
        }
    }
}
