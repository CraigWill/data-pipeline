package com.realtime.monitor.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 全局安全响应头过滤器
 * 
 * 为所有 HTTP 响应添加安全头部，防止脚本注入和 Cookie 窃取：
 * - X-Content-Type-Options: nosniff
 * - X-XSS-Protection: 1; mode=block
 * - X-Frame-Options: DENY
 * - Referrer-Policy: strict-origin-when-cross-origin
 * 
 * 同时确保 Set-Cookie 头包含 HttpOnly; Secure; SameSite=Strict 属性
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // No initialization needed
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (response instanceof HttpServletResponse httpResponse) {
            // Prevent MIME-type sniffing
            httpResponse.setHeader("X-Content-Type-Options", "nosniff");

            // Legacy XSS protection
            httpResponse.setHeader("X-XSS-Protection", "1; mode=block");

            // Prevent clickjacking
            httpResponse.setHeader("X-Frame-Options", "DENY");

            // Control referrer information
            httpResponse.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

            // Wrap response to enforce HttpOnly on all cookies
            chain.doFilter(request, new HttpOnlyCookieResponseWrapper(httpResponse));
        } else {
            chain.doFilter(request, response);
        }
    }

    @Override
    public void destroy() {
        // No cleanup needed
    }
}
