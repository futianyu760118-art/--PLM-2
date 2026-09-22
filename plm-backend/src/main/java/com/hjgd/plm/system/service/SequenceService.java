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
                    "SELECT prefix, date_pattern, length FROM sys_sequence WHERE seq_key = ? FOR UPDATE",
                    seqKey);
            jdbcTemplate.update(
                    "UPDATE sys_sequence SET current_val = current_val + 1, updated_at = now() WHERE seq_key = ?",
                    seqKey);
            Long currentVal = jdbcTemplate.queryForObject(
                    "SELECT current_val FROM sys_sequence WHERE seq_key = ?",
                    Long.class, seqKey);
            String prefix = (String) row.get("prefix");
            Integer length = ((Number) row.get("length")).intValue();
            String datePart = LocalDateTime.now().format(DATE_FMT);
            String seqPart = String.format("%0" + length + "d", currentVal);
            return prefix + datePart + seqPart;
        } finally {
            lock.unlock();
        }
    }
}
