package com.admin.server.modules.system.service.auth.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SliderImageRenderer 单元测试")
class SliderImageRendererTest {

    @Test
    @DisplayName("渲染结果返回背景图与拼图块，且不同坐标生成不同拼图")
    void rendersBackgroundAndPiece() {
        SliderImageRenderer renderer = new SliderImageRenderer();

        SliderImageRenderer.RenderedImage first = renderer.render(0, 160, 80);
        SliderImageRenderer.RenderedImage second = renderer.render(0, 190, 80);

        assertTrue(first.background().startsWith("data:image/jpeg;base64,"));
        assertTrue(first.piece().startsWith("data:image/png;base64,"));
        assertTrue(first.background().length() > 1000);
        assertTrue(first.piece().length() > 500);
        assertNotEquals(first.piece(), second.piece());

        // 负索引会被归一到最后一组背景，不应抛异常
        SliderImageRenderer.RenderedImage wrapped = renderer.render(-1, 160, 80);
        assertTrue(wrapped.background().startsWith("data:image/jpeg;base64,"));
    }
}
