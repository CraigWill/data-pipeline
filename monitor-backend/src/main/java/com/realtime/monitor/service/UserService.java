package com.realtime.monitor.service;

import com.realtime.monitor.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class UserService implements UserDetailsService {

    private final PasswordEncoder passwordEncoder;

    // 临时存储用户（生产环境应使用数据库）
    private final Map<String, User> users = new HashMap<>();

    public UserService(PasswordEncoder passwordEncoder,
                       @Value("${admin.initial-password:}") String initialPassword) {
        this.passwordEncoder = passwordEncoder;
        initDefaultUsers(initialPassword);
    }

    private void initDefaultUsers(String initialPassword) {
        if (!StringUtils.hasText(initialPassword)) {
            throw new IllegalStateException(
                "环境变量 ADMIN_INITIAL_PASSWORD 未设置！" +
                "请在 .env 文件中设置此变量。生成示例: openssl rand -base64 16");
        }
        User admin = new User();
        admin.setId(1L);
        admin.setUsername("admin");
        admin.setPassword(passwordEncoder.encode(initialPassword));
        admin.setEmail("admin@example.com");
        admin.setRole("ROLE_ADMIN");
        admin.setEnabled(true);
        admin.setCreatedAt(LocalDateTime.now());
        admin.setUpdatedAt(LocalDateTime.now());
        users.put("admin", admin);

        log.info("默认管理员账户已初始化: username=admin");
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = users.get(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .authorities(Collections.singletonList(new SimpleGrantedAuthority(user.getRole())))
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(!user.isEnabled())
                .build();
    }

    public boolean changePassword(String username, String oldPassword, String newPassword) {
        User user = users.get(username);
        if (user == null) {
            return false;
        }

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            return false;
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        return true;
    }

    public User getUserByUsername(String username) {
        return users.get(username);
    }
}
