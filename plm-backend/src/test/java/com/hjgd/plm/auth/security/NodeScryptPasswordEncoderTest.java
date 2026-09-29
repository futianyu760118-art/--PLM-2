package com.hjgd.plm.auth.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PAND-117 / ADR-2：EBMS scrypt 口令串（Node crypto.scryptSync 产出）必须能被 PLM2 运行时校验，
 * 且 BCrypt 分支与 Spring Security 互通。
 */
@DisplayName("NodeScryptPasswordEncoder: EBMS scrypt 与 BCrypt 双分支")
class NodeScryptPasswordEncoderTest {

    private final PasswordEncoder encoder = new NodeScryptPasswordEncoder();
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    /**
     * 已知答案向量：由 Node 生成
     * crypto.scryptSync('Ebms#2026', '00112233445566778899aabbccddeeff', 64).toString('hex')
     * <p>固定 salt 是为了钉死语义——尤其 salt 段是按「十六进制字符串的 UTF-8 字节」参与派生，
     * 而不是先 hex 解码；实现若写成解码，本用例会失败。
     */
    private static final String KAT =
            "$scrypt$00112233445566778899aabbccddeeff$"
            + "7466af924ef5f4958d6b27ef53f27d196870153519bb593ae9c66c7553377340"
            + "60c00e4354bf7496bddaaba92fbf6f195270d7f6f6525069a705cdca6a26fade";
    private static final String KAT_PWD = "Ebms#2026";

    @Nested
    @DisplayName("scrypt 分支")
    class ScryptBranch {

        @Test
        @DisplayName("Node 产出的 scrypt 串按原文校验通过（跨语言字节一致）")
        void shouldMatchNodeProducedScrypt() {
            assertTrue(encoder.matches(KAT_PWD, KAT));
        }

        @Test
        @DisplayName("错误口令不通过")
        void shouldRejectWrongPassword() {
            assertFalse(encoder.matches("Ebms#2026x", KAT));
            assertFalse(encoder.matches("", KAT));
        }

        @Test
        @DisplayName("哈希段大小写不敏感（salt 段按字节参与派生，必须原样）")
        void shouldNormalizeHashCase() {
            String[] parts = KAT.split("\\$");
            String upper = "$scrypt$" + parts[2] + "$" + parts[3].toUpperCase(java.util.Locale.ROOT);
            assertTrue(encoder.matches(KAT_PWD, upper));
        }

        @Test
        @DisplayName("isScrypt 只识别 $scrypt$ 前缀")
        void shouldDetectPrefix() {
            assertTrue(NodeScryptPasswordEncoder.isScrypt(KAT));
            assertFalse(NodeScryptPasswordEncoder.isScrypt(bcrypt.encode("x")));
            assertFalse(NodeScryptPasswordEncoder.isScrypt(null));
            assertFalse(NodeScryptPasswordEncoder.isScrypt(""));
        }
    }

    @Nested
    @DisplayName("BCrypt 分支")
    class BcryptBranch {

        @Test
        @DisplayName("encode 产出 $2a$ BCrypt，与 Spring BCryptPasswordEncoder 互通")
        void shouldProduceSpringCompatibleBcrypt() {
            String encoded = encoder.encode("passw0rd");
            assertTrue(encoded.startsWith("$2a$"), "前缀应为 $2a$，实际 " + encoded.substring(0, 4));
            assertTrue(encoder.matches("passw0rd", encoded));
            // 迁移工具用 Node bcryptjs 产出，Spring 必须认
            assertTrue(bcrypt.matches("passw0rd", encoded));
        }

        @Test
        @DisplayName("错误口令不通过")
        void shouldRejectWrongPassword() {
            assertFalse(encoder.matches("nope", encoder.encode("passw0rd")));
        }
    }

    @Nested
    @DisplayName("异常输入安全失败")
    class SafeFailure {

        @Test
        @DisplayName("null / 空 / 结构残缺一律返回 false，不抛异常")
        void shouldFailSafely() {
            assertFalse(encoder.matches("x", null));
            assertFalse(encoder.matches(null, KAT));
            assertFalse(encoder.matches("x", ""));
            assertFalse(encoder.matches("x", "$scrypt$$"));
            assertFalse(encoder.matches("x", "$scrypt$salt$"));
            assertFalse(encoder.matches("x", "not-a-hash"));
        }
    }
}
