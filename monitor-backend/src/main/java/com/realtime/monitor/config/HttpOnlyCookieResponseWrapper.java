package com.realtime.monitor.config;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

/**
 * HttpServletResponse 包装器，确保所有 Set-Cookie 头都包含安全属性：
 * - HttpOnly: 防止 JavaScript 访问 Cookie（防 XSS 窃取）
 * - Secure: 仅通过 HTTPS 传输
 * - SameSite=Strict: 防止 CSRF 攻击
 */
public class HttpOnlyCookieResponseWrapper extends HttpServletResponseWrapper {

    public HttpOnlyCookieResponseWrapper(HttpServletResponse response) {
        super(response);
    }

    @Override
    public void addHeader(String name, String value) {
        if ("Set-Cookie".equalsIgnoreCase(name)) {
            value = enforceSecureCookieAttributes(value);
        }
        super.addHeader(name, value);
    }

    @Override
    public void setHeader(String name, String value) {
        if ("Set-Cookie".equalsIgnoreCase(name)) {
            value = enforceSecureCookieAttributes(value);
        }
        super.setHeader(name, value);
    }

    /**
     * 确保 Cookie 值包含 HttpOnly、Secure 和 SameSite 属性
     */
    private String enforceSecureCookieAttributes(String cookieValue) {
        if (cookieValue == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder(cookieValue);

        // Add HttpOnly if not present
        if (!cookieValue.toLowerCase().contains("httponly")) {
            sb.append("; HttpOnly");
        }

        // Add Secure if not present
        if (!cookieValue.toLowerCase().contains("secure")) {
            sb.append("; Secure");
        }

        // Add SameSite=Strict if no SameSite attribute present
        if (!cookieValue.toLowerCase().contains("samesite")) {
            sb.append("; SameSite=Strict");
        }

        return sb.toString();
    }
}
