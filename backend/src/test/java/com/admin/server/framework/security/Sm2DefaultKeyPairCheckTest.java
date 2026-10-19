package com.admin.server.framework.security;

import com.admin.server.framework.security.core.util.Sm2Util;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 校验运行时生成的 SM2 密钥对确实配对，避免前端用公钥加密后服务端私钥无法解密。
 */
public class Sm2DefaultKeyPairCheckTest {

    @Test
    @DisplayName("SM2 生成的密钥对必须配对")
    void generatedKeyPairMustMatch() throws Exception {
        Sm2Util.Sm2KeyPair pair = Sm2Util.generateKeyPair();
        Assertions.assertEquals(130, pair.publicKeyHex().length());
        Assertions.assertEquals(64, pair.privateKeyHex().length());
        Assertions.assertTrue(pair.publicKeyHex().startsWith("04"));

        String plain = "GeneratedKeyPair@2026";
        String cipher = Sm2Util.encryptHex(pair.publicKeyHex(), plain);
        Assertions.assertEquals(plain, Sm2Util.decryptHex(pair.privateKeyHex(), cipher));
    }
}
