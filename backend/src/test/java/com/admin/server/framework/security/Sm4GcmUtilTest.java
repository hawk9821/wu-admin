package com.admin.server.framework.security;

import com.admin.server.framework.security.core.util.Sm4GcmUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class Sm4GcmUtilTest {

    private static final String HEX_KEY_32 = "0123456789abcdef0123456789abcdef";
    private static final String PLAIN_TEXT = "{\"username\":\"admin\",\"role\":\"super_admin\",\"timestamp\":1729255000000}";

    @Test
    @DisplayName("测试 SM4-GCM 加密与解密回环正常")
    public void testEncryptAndDecrypt() throws Exception {
        String cipherBase64 = Sm4GcmUtil.encryptToBase64(HEX_KEY_32, PLAIN_TEXT);
        Assertions.assertNotNull(cipherBase64);
        Assertions.assertTrue(Sm4GcmUtil.isSm4GcmBase64Payload(cipherBase64));

        String decrypted = Sm4GcmUtil.decryptFromBase64(HEX_KEY_32, cipherBase64);
        Assertions.assertEquals(PLAIN_TEXT, decrypted);
    }

    @Test
    @DisplayName("测试 SM4-GCM 密文被篡改时抛出认证失败异常 (防篡改检测)")
    public void testTamperedCiphertextFails() throws Exception {
        String cipherBase64 = Sm4GcmUtil.encryptToBase64(HEX_KEY_32, PLAIN_TEXT);
        byte[] bytes = org.bouncycastle.util.encoders.Base64.decode(cipherBase64);
        // 篡改最后一个字节 (Tag 的一部分)
        bytes[bytes.length - 1] ^= 0x01;
        String tamperedBase64 = org.bouncycastle.util.encoders.Base64.toBase64String(bytes);

        Assertions.assertThrows(Exception.class, () -> {
            Sm4GcmUtil.decryptFromBase64(HEX_KEY_32, tamperedBase64);
        });
    }
}
