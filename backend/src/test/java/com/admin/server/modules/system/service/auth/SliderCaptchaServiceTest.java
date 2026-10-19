package com.admin.server.modules.system.service.auth;

import cn.hutool.json.JSONUtil;
import com.admin.server.common.exception.BusinessException;
import com.admin.server.modules.system.api.auth.vo.SliderTrackEventVO;
import com.admin.server.modules.system.service.auth.support.SliderImageRenderer;
import com.admin.server.modules.system.service.config.SystemConfigHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SliderCaptchaService 单元测试")
class SliderCaptchaServiceTest {

    @Mock
    private RedissonClient redissonClient;
    @Mock
    private SystemConfigHelper systemConfigHelper;
    @Mock
    private SliderImageRenderer sliderImageRenderer;

    @InjectMocks
    private SliderCaptchaService sliderCaptchaService;

    @BeforeEach
    void setUpFailCounter() {
        RAtomicLong failCounter = mock(RAtomicLong.class);
        lenient().when(failCounter.incrementAndGet()).thenReturn(1L);
        lenient().when(redissonClient.getAtomicLong(anyString())).thenReturn(failCounter);
    }

    @Test
    @DisplayName("verifyAndConsume：拒绝 legacy slider_verified")
    void verifyAndConsume_rejectsLegacyBypass() {
        assertEquals("请完成滑块验证",
                sliderCaptchaService.verifyAndConsume("token", SliderCaptchaService.LEGACY_BYPASS_CODE));
    }

    @Test
    @DisplayName("verifyAndConsume：旧 challenge token 不再能直接作为凭证")
    void verifyAndConsume_rejectsLegacyChallengeToken() {
        assertEquals("滑块验证已过期，请重新验证",
                sliderCaptchaService.verifyAndConsume("plain-challenge-token", "182"));
    }

    @Test
    @DisplayName("verifyAndConsume：一次性票据消费成功后立即失效")
    void verifyAndConsume_consumesTicket() {
        @SuppressWarnings("unchecked")
        RBucket<String> bucket = mock(RBucket.class);
        when(redissonClient.<String>getBucket("captcha:slider:ticket:ct_slider_abc")).thenReturn(bucket);
        when(bucket.getAndDelete()).thenReturn("{}");

        assertNull(sliderCaptchaService.verifyAndConsume("ct_slider_abc", "182"));
    }

    @Test
    @DisplayName("verifyAndConsume：票据不存在或已过期时失败")
    void verifyAndConsume_rejectsExpiredTicket() {
        @SuppressWarnings("unchecked")
        RBucket<String> bucket = mock(RBucket.class);
        when(redissonClient.<String>getBucket("captcha:slider:ticket:ct_slider_expired")).thenReturn(bucket);
        when(bucket.getAndDelete()).thenReturn(null);

        assertEquals("滑块验证已过期，请重新验证",
                sliderCaptchaService.verifyAndConsume("ct_slider_expired", ""));
    }

    @Test
    @DisplayName("verifyAndConsume：票据场景与调用场景不一致时拒绝")
    void verifyAndConsume_rejectsSceneMismatch() {
        SliderCaptchaService.TicketState state = new SliderCaptchaService.TicketState();
        state.setScene("login");
        state.setClientIp("127.0.0.1");
        @SuppressWarnings("unchecked")
        RBucket<String> bucket = mock(RBucket.class);
        when(redissonClient.<String>getBucket("captcha:slider:ticket:ct_slider_scene")).thenReturn(bucket);
        when(bucket.getAndDelete()).thenReturn(JSONUtil.toJsonStr(state));

        assertEquals("安全验证已失效，请重新验证",
                sliderCaptchaService.verifyAndConsume("ct_slider_scene", "182", "register", "127.0.0.1"));
    }

    @Test
    @DisplayName("verifyAndConsume：票据客户端 IP 不一致时拒绝")
    void verifyAndConsume_rejectsIpMismatch() {
        SliderCaptchaService.TicketState state = new SliderCaptchaService.TicketState();
        state.setScene("login");
        state.setClientIp("1.2.3.4");
        @SuppressWarnings("unchecked")
        RBucket<String> bucket = mock(RBucket.class);
        when(redissonClient.<String>getBucket("captcha:slider:ticket:ct_slider_ip")).thenReturn(bucket);
        when(bucket.getAndDelete()).thenReturn(JSONUtil.toJsonStr(state));

        assertEquals("安全验证已失效，请重新验证",
                sliderCaptchaService.verifyAndConsume("ct_slider_ip", "182", "login", "5.6.7.8"));
    }

    @Test
    @DisplayName("verifyChallenge：轨迹与缺口匹配时签发一次性票据")
    void verifyChallenge_issuesTicket() {
        SliderCaptchaService.ChallengeState state = new SliderCaptchaService.ChallengeState();
        state.setTargetX(180);
        state.setPieceTop(74);
        state.setScene("login");
        state.setClientIp("127.0.0.1");
        state.setFailCount(0);
        state.setCreatedAt(System.currentTimeMillis());

        @SuppressWarnings("unchecked")
        RBucket<String> challengeBucket = mock(RBucket.class);
        @SuppressWarnings("unchecked")
        RBucket<String> ticketBucket = mock(RBucket.class);
        when(redissonClient.<String>getBucket("captcha:slider:challenge:abc")).thenReturn(challengeBucket);
        when(challengeBucket.get()).thenReturn(JSONUtil.toJsonStr(state));
        when(redissonClient.<String>getBucket(startsWith("captcha:slider:ticket:ct_slider_"))).thenReturn(ticketBucket);

        Map<String, Object> result = sliderCaptchaService.verifyChallenge(
                "abc", 180, 520, buildEvents(180), "127.0.0.1");

        assertNotNull(result.get("ticket"));
        assertTrue(String.valueOf(result.get("ticket")).startsWith("ct_slider_"));
        verify(challengeBucket).delete();
        verify(ticketBucket).set(anyString(), any());
    }

    @Test
    @DisplayName("verifyChallenge：偏移量错误时累计失败次数且不签发票据")
    void verifyChallenge_rejectsWrongOffset() {
        SliderCaptchaService.ChallengeState state = new SliderCaptchaService.ChallengeState();
        state.setTargetX(180);
        state.setPieceTop(74);
        state.setScene("login");
        state.setClientIp("127.0.0.1");
        state.setFailCount(0);
        state.setCreatedAt(System.currentTimeMillis());

        @SuppressWarnings("unchecked")
        RBucket<String> challengeBucket = mock(RBucket.class);
        when(redissonClient.<String>getBucket("captcha:slider:challenge:abc")).thenReturn(challengeBucket);
        when(challengeBucket.get()).thenReturn(JSONUtil.toJsonStr(state));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> sliderCaptchaService.verifyChallenge("abc", 100, 520, buildEvents(100), "127.0.0.1"));

        assertEquals("滑块验证失败，请重试", exception.getMessage());
        verify(challengeBucket).set(anyString(), any());
        verify(challengeBucket, never()).delete();
    }

    @Test
    @DisplayName("verifyChallenge：没有拖动轨迹的脚本请求直接拒绝")
    void verifyChallenge_rejectsMissingTrack() {
        SliderCaptchaService.ChallengeState state = new SliderCaptchaService.ChallengeState();
        state.setTargetX(180);
        state.setPieceTop(74);
        state.setScene("login");
        state.setClientIp("127.0.0.1");
        state.setFailCount(0);
        state.setCreatedAt(System.currentTimeMillis());

        @SuppressWarnings("unchecked")
        RBucket<String> challengeBucket = mock(RBucket.class);
        when(redissonClient.<String>getBucket("captcha:slider:challenge:abc")).thenReturn(challengeBucket);
        when(challengeBucket.get()).thenReturn(JSONUtil.toJsonStr(state));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> sliderCaptchaService.verifyChallenge("abc", 180, 520, List.of(), "127.0.0.1"));

        assertEquals("行为轨迹异常，请重新验证", exception.getMessage());
    }

    @Test
    @DisplayName("createChallenge：响应只包含渲染图片，不再泄露 targetX")
    void createChallenge_doesNotLeakTargetX() {
        when(systemConfigHelper.isCaptchaEnabled()).thenReturn(true);
        when(systemConfigHelper.getCaptchaType()).thenReturn(SystemConfigHelper.CAPTCHA_TYPE_SLIDER);
        when(systemConfigHelper.getCaptchaPerIpMinute()).thenReturn(40);

        RAtomicLong counter = mock(RAtomicLong.class);
        when(counter.incrementAndGet()).thenReturn(1L);
        when(redissonClient.getAtomicLong(anyString())).thenReturn(counter);

        when(sliderImageRenderer.render(anyInt(), anyInt(), anyInt()))
                .thenReturn(new SliderImageRenderer.RenderedImage("data:image/jpeg;base64,bg", "data:image/png;base64,piece"));

        @SuppressWarnings("unchecked")
        RBucket<String> bucket = mock(RBucket.class);
        when(redissonClient.<String>getBucket(startsWith("captcha:slider:challenge:"))).thenReturn(bucket);

        Map<String, Object> result = sliderCaptchaService.createChallenge("login", "127.0.0.1");

        assertNotNull(result.get("token"));
        assertNotNull(result.get("background"));
        assertNotNull(result.get("piece"));
        assertNotNull(result.get("pieceTop"));
        assertEquals(340, result.get("width"));
        assertEquals(52, result.get("pieceSize"));
        assertNull(result.get("targetX"));
        verify(bucket).set(anyString(), any());
    }

    private List<SliderTrackEventVO> buildEvents(int endX) {
        List<SliderTrackEventVO> events = new ArrayList<>();
        int steps = 10;
        for (int i = 0; i <= steps; i++) {
            SliderTrackEventVO event = new SliderTrackEventVO();
            event.setX(endX * i / steps);
            event.setY(2);
            event.setT(i * 50);
            events.add(event);
        }
        return events;
    }
}
