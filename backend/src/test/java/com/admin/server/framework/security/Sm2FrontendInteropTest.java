package com.admin.server.framework.security;

import com.admin.server.framework.security.core.util.Sm2Util;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 前后端 SM2 互通回归测试。
 *
 * <p>测试向量由前端 {@code frontend/src/utils/crypto.ts} 使用固定测试公钥、sm-crypto
 * {@code doEncrypt(..., 1)}（C1C3C2）生成。sm-crypto 输出的 C1 不带 04 前缀，
 * 后端必须能正确识别并解密；任一端格式漂移都会导致本测试失败。</p>
 *
 * <p>这里的密钥对是测试专用常量，仅用于固定向量，与生产运行时动态托管的密钥无关。</p>
 */
public class Sm2FrontendInteropTest {

    /** 测试专用公钥（04 开头未压缩），与下方私钥配对 */
    private static final String TEST_PUBLIC_KEY =
            "04a3ab9ad67089e840de94297dc1be1e6f1961daa6eccb7107ad03e229c6a04b933f1ecaa78252da6ccdd135089c8b194b6923bca76a2ec64d999825875e559df1";
    /** 测试专用私钥（32 字节 Hex） */
    private static final String TEST_PRIVATE_KEY =
            "94aa3b522664c4bf04a4957f4e9c463859ebf49d006dd786c1e5dd4e79b50209";

    /** sm-crypto 生成的固定密文：x||y||C3||C2，不含 04 前缀 */
    private static final String FRONTEND_CIPHER =
            "d01c3dcb3c1d838cfd277139cf6507b098a525c781fcffef3b728595d801ae61"
            + "26cbc91108344d86bbaf2031fe7d6ff1f0bfeeb1f1b74cd85754c7d185974320"
            + "b9b2137a97955a6aeb320be76f8fd6dc6cc0d04da989a0439fd63bd4c93d1ba6"
            + "6ed176bf43b6208b6b6b013625090e";
    private static final String FRONTEND_PLAIN = "Sm2Interop@2026";

    @Test
    @DisplayName("测试公钥与私钥必须配对")
    void testKeyPairMustMatch() throws Exception {
        String cipher = Sm2Util.encryptHex(TEST_PUBLIC_KEY, FRONTEND_PLAIN);
        Assertions.assertEquals(FRONTEND_PLAIN, Sm2Util.decryptHex(TEST_PRIVATE_KEY, cipher));
    }

    @Test
    @DisplayName("sm-crypto 无 04 前缀密文可被后端解密")
    void decryptFrontendCipherWithoutPrefix() throws Exception {
        String plain = Sm2Util.decryptHex(TEST_PRIVATE_KEY, FRONTEND_CIPHER);
        Assertions.assertEquals(FRONTEND_PLAIN, plain);
    }

    @Test
    @DisplayName("带 sm2: 前缀的前端密文走口令解密入口")
    void decryptFrontendCipherWithPrefix() {
        String plain = Sm2Util.decryptPasswordIfEncrypted(TEST_PRIVATE_KEY, "sm2:" + FRONTEND_CIPHER);
        Assertions.assertEquals(FRONTEND_PLAIN, plain);
    }
}
