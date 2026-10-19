package com.admin.server.framework.security.core;

import cn.hutool.json.JSONUtil;
import com.admin.server.framework.security.core.util.Sm2Util;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SM2 运行时密钥对管理")
class Sm2KeyManagerTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private Sm2KeyManager keyManager;

    @BeforeEach
    void setUp() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("共享存储为空时生成密钥对并原子写入")
    void generatesAndPersistsWhenAbsent() {
        when(valueOperations.get(Sm2KeyManager.REDIS_KEY)).thenReturn(null);
        when(valueOperations.setIfAbsent(eq(Sm2KeyManager.REDIS_KEY), anyString())).thenReturn(true);

        Sm2Util.Sm2KeyPair pair = keyManager.getKeyPair();

        assertNotNull(pair);
        assertEquals(130, pair.publicKeyHex().length());
        assertEquals(64, pair.privateKeyHex().length());
        verify(valueOperations).setIfAbsent(eq(Sm2KeyManager.REDIS_KEY), anyString());
    }

    @Test
    @DisplayName("共享存储已有密钥对时直接复用，不再生成")
    void readsExistingPair() {
        Sm2Util.Sm2KeyPair pair = Sm2Util.generateKeyPair();
        String json = JSONUtil.toJsonStr(Map.of(
                "publicKey", pair.publicKeyHex(),
                "privateKey", pair.privateKeyHex()));
        when(valueOperations.get(Sm2KeyManager.REDIS_KEY)).thenReturn(json);

        assertEquals(pair.publicKeyHex(), keyManager.getPublicKey());
        assertEquals(pair.privateKeyHex(), keyManager.getPrivateKey());
        verify(valueOperations, never()).setIfAbsent(anyString(), anyString());
    }

    @Test
    @DisplayName("Redis 不可用时退化为进程内临时密钥对且保持稳定")
    void fallsBackToInMemoryWhenRedisFails() {
        when(valueOperations.get(Sm2KeyManager.REDIS_KEY))
                .thenThrow(new RuntimeException("redis down"));

        Sm2Util.Sm2KeyPair pair = keyManager.getKeyPair();

        assertNotNull(pair);
        assertEquals(pair.publicKeyHex(), keyManager.getPublicKey());
        assertEquals(pair.privateKeyHex(), keyManager.getPrivateKey());
    }

    @Test
    @DisplayName("轮换后重新生成一对新密钥")
    void rotateRegeneratesKeyPair() {
        when(valueOperations.get(Sm2KeyManager.REDIS_KEY)).thenReturn(null);
        when(valueOperations.setIfAbsent(eq(Sm2KeyManager.REDIS_KEY), anyString())).thenReturn(true);

        Sm2Util.Sm2KeyPair first = keyManager.getKeyPair();
        keyManager.rotate();
        Sm2Util.Sm2KeyPair second = keyManager.getKeyPair();

        assertNotEquals(first.publicKeyHex(), second.publicKeyHex());
        verify(stringRedisTemplate).delete(Sm2KeyManager.REDIS_KEY);
    }
}
