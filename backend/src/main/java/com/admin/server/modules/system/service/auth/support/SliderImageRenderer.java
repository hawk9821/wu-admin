package com.admin.server.modules.system.service.auth.support;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 滑块验证码图片渲染器。
 * <p>缺口坐标只在服务端参与渲染与校验，响应中仅返回合成后的背景图和拼图块，
 * 从根源上避免把正确答案（targetX）暴露给客户端。</p>
 */
@Component
public class SliderImageRenderer {

    /** 与 PC 端滑块弹窗默认内容宽度一致 */
    public static final int WIDTH = 340;

    public static final int HEIGHT = 200;

    public static final int PIECE_SIZE = 52;

    /** 背景图片总数（resources/captcha/bg1.jpg ~ bgN.jpg） */
    public static final int BG_COUNT = 28;

    private static final Map<Integer, BufferedImage> BACKGROUND_CACHE = new ConcurrentHashMap<>();

    /**
     * 生成一次挑战所需的背景图与拼图块。
     *
     * @param bgIndex 背景图序号（0 基）
     * @param targetX 缺口左上角 X（仅服务端持有）
     * @param targetY 缺口左上角 Y（响应中会以 pieceTop 返回，用于前端定位拼图块）
     */
    public RenderedImage render(int bgIndex, int targetX, int targetY) {
        BufferedImage canvas = copyOf(loadBackground(bgIndex));
        BufferedImage piece = crop(canvas, targetX, targetY, PIECE_SIZE);
        darken(canvas, targetX, targetY, PIECE_SIZE);
        return new RenderedImage(toDataUrl(canvas, "jpg"), toDataUrl(piece, "png"));
    }

    private BufferedImage copyOf(BufferedImage source) {
        BufferedImage out = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = out.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, 0, 0, WIDTH, HEIGHT, null);
        } finally {
            graphics.dispose();
        }
        return out;
    }

    private BufferedImage crop(BufferedImage source, int x, int y, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = out.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, size, size, x, y, x + size, y + size, null);
        } finally {
            graphics.dispose();
        }
        return out;
    }

    private void darken(BufferedImage image, int x, int y, int size) {
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
            graphics.setColor(Color.BLACK);
            graphics.fillRect(x, y, size, size);
        } finally {
            graphics.dispose();
        }
    }

    private BufferedImage loadBackground(int bgIndex) {
        int normalized = Math.floorMod(bgIndex, BG_COUNT) + 1;
        return BACKGROUND_CACHE.computeIfAbsent(normalized, this::readBackground);
    }

    private BufferedImage readBackground(int number) {
        String path = "/captcha/bg" + number + ".jpg";
        try (InputStream input = getClass().getResourceAsStream(path)) {
            if (input != null) {
                BufferedImage image = ImageIO.read(input);
                if (image != null) {
                    return image;
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // 资源缺失或损坏时回退到程序生成背景，避免验证码接口整体不可用
        }
        return proceduralBackground(number);
    }

    /**
     * 兜底背景：在没有随包背景图（或图片损坏）时仍然能够出题。
     */
    private BufferedImage proceduralBackground(int seed) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            Random random = new Random(seed * 7919L);
            graphics.setPaint(new GradientPaint(
                    0, 0,
                    new Color(40 + random.nextInt(60), 70 + random.nextInt(60), 100 + random.nextInt(60)),
                    WIDTH, HEIGHT,
                    new Color(100 + random.nextInt(80), 130 + random.nextInt(80), 160 + random.nextInt(80))));
            graphics.fillRect(0, 0, WIDTH, HEIGHT);
            for (int i = 0; i < 140; i++) {
                int radius = 8 + random.nextInt(28);
                graphics.setColor(new Color(255, 255, 255, 18));
                graphics.fillOval(random.nextInt(WIDTH), random.nextInt(HEIGHT), radius, radius);
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private String toDataUrl(BufferedImage image, String format) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, format, output);
            String mime = "jpg".equals(format) ? "image/jpeg" : "image/png";
            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("滑块验证码图片编码失败", e);
        }
    }

    /**
     * 渲染结果：背景图已挖出缺口，拼图块为缺口位置的原始切片。
     */
    public record RenderedImage(String background, String piece) {
    }
}
