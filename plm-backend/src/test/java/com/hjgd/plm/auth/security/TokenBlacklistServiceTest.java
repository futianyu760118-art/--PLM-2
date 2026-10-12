package com.hjgd.plm.auth.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PAND-117 / R2 / AC6.5：登出后令牌立即失效，且名单不随过期令牌无界增长。
 */
@DisplayName("TokenBlacklistService: 登出令牌失效名单")
class TokenBlacklistServiceTest {

    private static final long HOUR = 3600_000L;

    @Test
    @DisplayName("未拉黑的 jti 不视为失效")
    void unknownJtiIsNotRevoked() {
        TokenBlacklistService svc = new TokenBlacklistService();
        assertFalse(svc.isRevoked("jti-1"));
    }

    @Test
    @DisplayName("拉黑后立即失效")
    void revokedJtiIsRejected() {
        TokenBlacklistService svc = new TokenBlacklistService();
        svc.revoke("jti-1", System.currentTimeMillis() + HOUR);
        assertTrue(svc.isRevoked("jti-1"));
        assertFalse(svc.isRevoked("jti-2"));
    }

    @Test
    @DisplayName("已过期的令牌不予记录（无意义，且会造成名单膨胀）")
    void alreadyExpiredTokenIsNotRecorded() {
        TokenBlacklistService svc = new TokenBlacklistService();
        svc.revoke("jti-old", System.currentTimeMillis() - HOUR);
        assertEquals(0, svc.size());
        assertFalse(svc.isRevoked("jti-old"));
    }

    @Test
    @DisplayName("过期条目读取时被回收，名单不无界增长")
    void expiredEntriesAreReclaimed() {
        TokenBlacklistService svc = new TokenBlacklistService();
        svc.revoke("jti-expired", System.currentTimeMillis() + 30);
        svc.revoke("jti-live", System.currentTimeMillis() + HOUR);
        assertEquals(2, svc.size());

        waitMillis(60);

        assertFalse(svc.isRevoked("jti-expired"), "过期令牌自然失效");
        // isRevoked 命中过期项时顺手移除
        assertEquals(1, svc.size());
        assertTrue(svc.isRevoked("jti-live"), "未过期令牌仍应保持失效");
    }

    @Test
    @DisplayName("null / 空 jti 安全忽略")
    void nullJtiIsIgnored() {
        TokenBlacklistService svc = new TokenBlacklistService();
        assertDoesNotThrow(() -> svc.revoke(null, System.currentTimeMillis() + HOUR));
        assertDoesNotThrow(() -> svc.revoke("", System.currentTimeMillis() + HOUR));
        assertFalse(svc.isRevoked(null));
        assertFalse(svc.isRevoked(""));
        assertEquals(0, svc.size());
    }

    private void waitMillis(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("等待被中断");
        }
    }
}
