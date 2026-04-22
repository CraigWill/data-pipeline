package com.realtime.monitor.config;

import com.realtime.monitor.security.JwtAuthenticationEntryPoint;
import com.realtime.monitor.security.JwtTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Autowired
    public SecurityConfig(JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint) {
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, 
                                          JwtTokenProvider tokenProvider,
                                          UserDetailsService userDetailsService) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.disable())
            .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthenticationEntryPoint))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers
                // X-Content-Type-Options: nosniff — 防止 MIME 类型嗅探
                .contentTypeOptions(contentType -> {})
                // X-Frame-Options: DENY — 防止点击劫持
                .frameOptions(frame -> frame.deny())
                // X-XSS-Protection: 1; mode=block
                .xssProtection(xss -> xss.headerValue(
                    org.springframework.security.web.header.writers.XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
            )
            .authorizeHttpRequests(auth -> auth
                // Allow async re-dispatches (SSE) without re-authentication
                .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                // 公开端点
                .requestMatchers("/api/auth/login", "/api/auth/logout").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                // 所有其他 API 需要认证（包括 SSE 流端点）
                .requestMatchers("/api/**").authenticated()
                .anyRequest().authenticated()
            );

        // 添加 JWT 过滤器（内联创建，避免循环依赖）
        http.addFilterBefore(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(@NonNull HttpServletRequest request,
                                          @NonNull HttpServletResponse response,
                                          @NonNull FilterChain filterChain) throws ServletException, IOException {
                try {
                    String jwt = getJwtFromRequest(request);

                    if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                        String username = tokenProvider.getUsernameFromToken(jwt);

                        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                } catch (Exception ex) {
                    log.error("Could not set user authentication in security context", ex);
                }

                filterChain.doFilter(request, response);
            }

            private String getJwtFromRequest(HttpServletRequest request) {
                String bearerToken = request.getHeader("Authorization");
                if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
                    return bearerToken.substring(7);
                }
                return null;
            }
        }, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
