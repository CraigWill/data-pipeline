package com.realtime.monitor.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.realtime.monitor.dto.ApiResponse;
import com.realtime.monitor.security.JwtTokenProvider;
import com.realtime.monitor.service.CaptchaService;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final CaptchaService captchaService;

    /**
     * 获取验证码图片
     */
    @GetMapping("/captcha")
    public ApiResponse<Map<String, String>> getCaptcha() {
        Map<String, String> captcha = captchaService.generateCaptcha();
        return ApiResponse.success(captcha);
    }

    /**
     * 登录（需要验证码）
     */
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody LoginRequest loginRequest) {
        try {
            // 验证码校验
            if (loginRequest.getCaptchaId() == null || loginRequest.getCaptchaCode() == null) {
                return ApiResponse.error("请输入验证码");
            }
            if (!captchaService.verify(loginRequest.getCaptchaId(), loginRequest.getCaptchaCode())) {
                return ApiResponse.error("验证码错误或已过期");
            }

            // 用户名密码认证
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getUsername(),
                            loginRequest.getPassword()
                    )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            String token = tokenProvider.generateToken(authentication);

            Map<String, Object> data = new HashMap<>();
            data.put("token", token);
            data.put("username", authentication.getName());
            data.put("authorities", authentication.getAuthorities());

            log.info("用户登录成功: {}", loginRequest.getUsername());
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("登录失败: {}", e.getMessage());
            return ApiResponse.error("用户名或密码错误");
        }
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        SecurityContextHolder.clearContext();
        return ApiResponse.success(null);
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return ApiResponse.error("未登录");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("username", authentication.getName());
        data.put("authorities", authentication.getAuthorities());
        return ApiResponse.success(data);
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
        private String captchaId;
        private String captchaCode;
    }
}
