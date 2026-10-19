package com.admin.server.framework.security;

import cn.hutool.core.util.HexUtil;
import com.admin.server.framework.security.core.util.Sm4GcmUtil;
import org.bouncycastle.util.encoders.Base64;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

/**
 * 前后端 SM4-GCM 互通回归测试。
 *
 * <p>测试向量由前端 {@code frontend/src/utils/sm4.ts} 在固定密钥、固定 IV 下生成，
 * 后端必须能逐字节还原；反向由后端生成，前端 {@code sm4.test.ts} 中保留同一向量，
 * 任一端算法实现漂移都会导致本测试失败。</p>
 */
public class Sm4GcmInteropTest {

    /** 32 位 Hex 密钥（等价 16 字节 0x01..0xef 交替） */
    private static final String KEY_HEX = "0123456789abcdef0123456789abcdef";
    /** 固定 12 字节 IV，保证密文可复现 */
    private static final byte[] FIXED_IV = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};

    /** 前端 sm4.ts 生成的固定向量：Base64(IV + Ciphertext + Tag) */
    private static final String FE_VECTOR = "AQIDBAUGBwgJCgsMEAWh/lS6IyG+LeHpobWYz1rNeXHvIvlCoz05xRq2XJ/R4ILqMX3I";
    private static final String FE_EMPTY = "AQIDBAUGBwgJCgsMdvgrI48xl1DnmkyaW1aLSQ==";
    private static final String FE_UNICODE = "AQIDBAUGBwgJCgsMt89ja7FtqfVBoyEOPHxhI0r7xy55hBqbkOHn5/vhXwM=";

    @Test
    @DisplayName("后端可解密前端固定向量（ASCII / 空串 / 中文 emoji）")
    public void decryptFrontendVectors() throws Exception {
        Assertions.assertEquals("CrossPlatformValidation", Sm4GcmUtil.decryptFromBase64(KEY_HEX, FE_VECTOR));
        Assertions.assertEquals("", Sm4GcmUtil.decryptFromBase64(KEY_HEX, FE_EMPTY));
        Assertions.assertEquals("中文测试😀", Sm4GcmUtil.decryptFromBase64(KEY_HEX, FE_UNICODE));
    }

    @Test
    @DisplayName("后端在相同密钥与 IV 下产生的密文与前端逐字节一致")
    public void encryptMatchesFrontendVectors() throws Exception {
        byte[] key = Sm4GcmUtil.resolveKeyBytes(KEY_HEX);
        Assertions.assertEquals(FE_VECTOR, encryptToBase64WithFixedIv(key, "CrossPlatformValidation"));
        Assertions.assertEquals(FE_EMPTY, encryptToBase64WithFixedIv(key, ""));
        Assertions.assertEquals(FE_UNICODE, encryptToBase64WithFixedIv(key, "中文测试😀"));
    }

    @Test
    @DisplayName("32 位 Hex 密钥解析为 16 字节，且两种密钥字符串写法等价")
    public void resolveKeyBytesEquivalence() throws Exception {
        Assertions.assertEquals(KEY_HEX, HexUtil.encodeHexStr(Sm4GcmUtil.resolveKeyBytes(KEY_HEX)));
        // 16 字符文本密钥按 ISO-8859-1 取字节，加解密需使用同一字符串写法
        String textKey = "0123456789abcdef";
        String cipher = Sm4GcmUtil.encryptToBase64(textKey, "text-key");
        Assertions.assertEquals("text-key", Sm4GcmUtil.decryptFromBase64(textKey, cipher));
    }

    /** 使用固定 IV 加密并按前后端约定拼接 Base64(IV[12] + Ciphertext + Tag[16]) */
    private static String encryptToBase64WithFixedIv(byte[] key, String plain) throws Exception {
        byte[] cipherWithTag = Sm4GcmUtil.encrypt(key, FIXED_IV, plain.getBytes(StandardCharsets.UTF_8), null);
        byte[] combined = new byte[FIXED_IV.length + cipherWithTag.length];
        System.arraycopy(FIXED_IV, 0, combined, 0, FIXED_IV.length);
        System.arraycopy(cipherWithTag, 0, combined, FIXED_IV.length, cipherWithTag.length);
        return Base64.toBase64String(combined);
    }
}
