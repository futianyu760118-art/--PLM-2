package com.hjgd.plm.auth.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登出令牌黑名单（R2 / AC6.5）：无状态 JWT 的失效名单，按 jti 记录。
 *
 * <p>默认单实例内存实现，条目在令牌自身过期时刻之后自动回收，不会无界增长。
 * 多实例部署时本类是实现替换点（换成共享存储，如 Redis），调用方无需改动。
 */
@Slf4j
@Service
public class TokenBlacklistService {

    /** jti → 令牌过期时刻(epoch millis) */
    private final Map<String, Long> blacklist = new ConcurrentHashMap<>();

    /** 拉黑一个令牌；expireAtMillis 为令牌自身过期时间，早于现在的无需记录 */
    public void revoke(String jti, long expireAtMillis) {
        if (jti == null || jti.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (expireAtMillis <= now) {
            return;
        }
        purgeExpired(now);
        blacklist.put(jti, expireAtMillis);
    }

    public boolean isRevoked(String jti) {
        if (jti == null || jti.isEmpty()) {
            return false;
        }
        Long expireAt = blacklist.get(jti);
        if (expireAt == null) {
            return false;
        }
        if (expireAt <= System.currentTimeMillis()) {
            blacklist.remove(jti);
            return false;
        }
        return true;
    }

    /** 当前名单规模，供运维观测/自检 */
    public int size() {
        return blacklist.size();
    }

    private void purgeExpired(long now) {
        blacklist.entrySet().removeIf(e -> e.getValue() <= now);
    }
}
