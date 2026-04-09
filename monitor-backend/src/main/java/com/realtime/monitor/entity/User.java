package com.realtime.monitor.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class User {
    private Long id;
    private String username;
    private String password; // BCrypt 加密后的密码
    private String email;
    private String role; // ROLE_ADMIN, ROLE_USER
    private boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
