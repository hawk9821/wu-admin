package com.admin.server.framework.security;

import com.admin.server.framework.security.core.util.Sm2Util;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

public class Sm2UtilTest {

    @Test
    @DisplayName("测试满血标准 SM2 密钥对生成、C1C3C2加解密与防篡改")
    public void testSm2EncryptAndDecrypt() throws Exception {
        Sm2Util.Sm2KeyPair keyPair = Sm2Util.generateKeyPair();
        System.out.println("GEN_SM2_PUB=" + keyPair.publicKeyHex());
        System.out.println("GEN_SM2_PRI=" + keyPair.privateKeyHex());
        Assertions.assertNotNull(keyPair.publicKeyHex());
        Assertions.assertNotNull(keyPair.privateKeyHex());
        Assertions.assertEquals(130, keyPair.publicKeyHex().length());
        Assertions.assertEquals(64, keyPair.privateKeyHex().length());
        Assertions.assertTrue(keyPair.publicKeyHex().startsWith("04"));

        String rawPassword = "Admin@2026#SecurePassword!";
        // 公钥加密
        String cipherHex = Sm2Util.encryptHex(keyPair.publicKeyHex(), rawPassword);
        Assertions.assertNotNull(cipherHex);
        Assertions.assertTrue(cipherHex.length() > 130);

        // 私钥解密
        String decrypted = Sm2Util.decryptHex(keyPair.privateKeyHex(), cipherHex);
        Assertions.assertEquals(rawPassword, decrypted);
    }

    @Test
    @DisplayName("测试 SM2 数字签名与公钥验签")
    public void testSm2SignAndVerify() throws Exception {
        Sm2Util.Sm2KeyPair keyPair = Sm2Util.generateKeyPair();
        String data = "GET\n/auth/user-info\n1729255000000\nabcdef0123456789";
        byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);

        // 私钥签名
        String signatureHex = Sm2Util.signHex(keyPair.privateKeyHex(), dataBytes);
        Assertions.assertNotNull(signatureHex);

        // 公钥验签通过
        boolean valid = Sm2Util.verifyHex(keyPair.publicKeyHex(), dataBytes, signatureHex);
        Assertions.assertTrue(valid);

        // 数据被篡改后验签失败
        byte[] tamperedBytes = (data + "tampered").getBytes(StandardCharsets.UTF_8);
        boolean tamperedValid = Sm2Util.verifyHex(keyPair.publicKeyHex(), tamperedBytes, signatureHex);
        Assertions.assertFalse(tamperedValid);
    }

    @Test
    @DisplayName("测试解密前端/移动端 TypeScript 满血加密的 SM2 口令密文")
    public void testDecryptFrontendSm2Cipher() {
        // 测试专用固定私钥，仅用于验证互通向量，不是生产密钥
        String priKeyHex = "94aa3b522664c4bf04a4957f4e9c463859ebf49d006dd786c1e5dd4e79b50209";
        String frontendCipher = "sm2:6b654431262ddbb2d9239f69489089bde7f5b2c5f395dbcfe94c654df712fb458f034cd977e2baaf44f2a0a37634545d193f971003ed365f60242f568703c289851daf48859bb97c3c67d177f74d732cbdb8c42f70472737654d03e2fe315a057de8fb54e97d79661108c94e5539e9c781";

        String plainText = Sm2Util.decryptPasswordIfEncrypted(priKeyHex, frontendCipher);
        Assertions.assertEquals("Admin@WuAdmin2026", plainText);
    }
}
