package com.hjgd.plm.auth.security;

import org.bouncycastle.crypto.generators.SCrypt;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * 复合口令编码器：BCrypt 为主，兼容 EBMS 迁移遗留的 Node scrypt 串（ADR-2）。
 *
 * <p>Store 前缀为 {@code $scrypt$} 时按 scrypt 校验，否则走 BCrypt。
 * 登录链路只需替换 {@code PasswordEncoder} bean 即可获得双算法校验能力，
 * 命中 scrypt 后由 {@code AuthController} 透明重哈希为 BCrypt 并清空 legacy_password。
 *
 * <p><b>参数必须与 EBMS(Node) 严格一致</b>：{@code crypto.scryptSync(pwd, salt, 64)}
 * 的默认参数为 N=16384, r=8, p=1, dkLen=64；
 * 且 Node 收到字符串 salt 时按 <b>UTF-8 字节</b>使用，而非 hex 解码后的字节——
 * 二者结果不同，此处按 UTF-8 字节实现，否则存量密码全部校验失败。
 */
public class NodeScryptPasswordEncoder implements PasswordEncoder {

    public static final String SCRYPT_PREFIX = "$scrypt$";

    /** 与 Node crypto.scryptSync 默认值对齐，不可随意调整 */
    private static final int N = 16384;
    private static final int R = 8;
    private static final int P = 1;
    private static final int DK_LEN = 64;

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    /** 是否为 EBMS 遗留 scrypt 串 */
    public static boolean isScrypt(String stored) {
        return stored != null && stored.startsWith(SCRYPT_PREFIX);
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return bcrypt.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (encodedPassword == null || rawPassword == null) {
            return false;
        }
        if (isScrypt(encodedPassword)) {
            return matchesScrypt(rawPassword, encodedPassword);
        }
        return bcrypt.matches(rawPassword, encodedPassword);
    }

    private boolean matchesScrypt(CharSequence rawPassword, String stored) {
        // 形如 $scrypt$<salt-hex>$<hash-hex>，split 后为 ["", "scrypt", salt, hash]
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || parts[2].isEmpty() || parts[3].isEmpty()) {
            return false;
        }
        byte[] salt = parts[2].getBytes(StandardCharsets.UTF_8);
        byte[] expected = parts[3].toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        byte[] derived = SCrypt.generate(
                rawPassword.toString().getBytes(StandardCharsets.UTF_8), salt, N, R, P, DK_LEN);
        byte[] actual = Hex.toHexString(derived).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(actual, expected);
    }
}
