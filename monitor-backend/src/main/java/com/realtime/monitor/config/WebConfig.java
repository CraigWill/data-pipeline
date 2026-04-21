package com.realtime.monitor.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置
 *
 * CORS 由 nginx 反向代理统一管理（见 monitor/frontend-vue/nginx.conf）。
 * Spring 端不再设置 CORS 头，避免与 nginx 产生双重头冲突。
 * 本地开发时前端 Vite dev server 自带 proxy 配置，同样不需要 Spring CORS。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    // CORS handled by nginx — no Spring-level CORS config needed
}
