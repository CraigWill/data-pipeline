package com.realtime.monitor.service;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * 验证码服务
 * 
 * 生成数学运算验证码图片，防止暴力破解登录。
 * 验证码有效期 5 分钟，使用后立即失效（一次性）。
 */
@Slf4j
@Service
public class CaptchaService {

    /** 验证码存储：captchaId -> {answer, expireTime} */
    private final ConcurrentHashMap<String, CaptchaEntry> captchaStore = new ConcurrentHashMap<>();

    /** 验证码有效期（毫秒）：5 分钟 */
    private static final long CAPTCHA_TTL = 5 * 60 * 1000;

    /** 图片宽度 */
    private static final int WIDTH = 120;
    /** 图片高度 */
    private static final int HEIGHT = 40;

    private final Random random = new Random();

    /**
     * 生成验证码
     * @return {captchaId, imageBase64}
     */
    public Map<String, String> generateCaptcha() {
        // 清理过期验证码
        cleanExpired();

        // 生成数学题
        int a = random.nextInt(20) + 1;
        int b = random.nextInt(20) + 1;
        int operator = random.nextInt(3); // 0=加, 1=减, 2=乘
        String expression;
        int answer;

        switch (operator) {
            case 0:
                expression = a + " + " + b + " = ?";
                answer = a + b;
                break;
            case 1:
                // 确保结果为正数
                if (a < b) { int tmp = a; a = b; b = tmp; }
                expression = a + " - " + b + " = ?";
                answer = a - b;
                break;
            default:
                a = random.nextInt(9) + 1;
                b = random.nextInt(9) + 1;
                expression = a + " × " + b + " = ?";
                answer = a * b;
                break;
        }

        // 生成图片
        String imageBase64 = generateImage(expression);

        // 存储验证码
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        captchaStore.put(captchaId, new CaptchaEntry(String.valueOf(answer), System.currentTimeMillis() + CAPTCHA_TTL));

        log.debug("生成验证码: id={}, expression={}", captchaId, expression);

        return Map.of(
                "captchaId", captchaId,
                "image", "data:image/png;base64," + imageBase64
        );
    }

    /**
     * 验证验证码
     * @param captchaId 验证码 ID
     * @param userInput 用户输入的答案
     * @return 是否正确
     */
    public boolean verify(String captchaId, String userInput) {
        if (captchaId == null || userInput == null || userInput.isBlank()) {
            return false;
        }

        CaptchaEntry entry = captchaStore.remove(captchaId); // 一次性使用
        if (entry == null) {
            log.warn("验证码不存在或已使用: {}", captchaId);
            return false;
        }

        if (System.currentTimeMillis() > entry.expireTime) {
            log.warn("验证码已过期: {}", captchaId);
            return false;
        }

        return entry.answer.equals(userInput.trim());
    }

    /**
     * 生成验证码图片（Base64 PNG）
     */
    private String generateImage(String text) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        // 背景
        g.setColor(new Color(240, 240, 240));
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // 干扰线
        for (int i = 0; i < 5; i++) {
            g.setColor(new Color(random.nextInt(200), random.nextInt(200), random.nextInt(200)));
            g.drawLine(random.nextInt(WIDTH), random.nextInt(HEIGHT),
                    random.nextInt(WIDTH), random.nextInt(HEIGHT));
        }

        // 干扰点
        for (int i = 0; i < 30; i++) {
            g.setColor(new Color(random.nextInt(200), random.nextInt(200), random.nextInt(200)));
            g.fillOval(random.nextInt(WIDTH), random.nextInt(HEIGHT), 2, 2);
        }

        // 文字
        g.setFont(new Font("Arial", Font.BOLD, 20));
        g.setColor(new Color(50, 50, 150));
        g.drawString(text, 10, 28);

        g.dispose();

        // 转 Base64
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            log.error("生成验证码图片失败", e);
            return "";
        }
    }

    /**
     * 清理过期验证码
     */
    private void cleanExpired() {
        long now = System.currentTimeMillis();
        captchaStore.entrySet().removeIf(entry -> now > entry.getValue().expireTime);
    }

    private static class CaptchaEntry {
        final String answer;
        final long expireTime;

        CaptchaEntry(String answer, long expireTime) {
            this.answer = answer;
            this.expireTime = expireTime;
        }
    }
}
