package com.att.tdp.issueflow.security.auth;

import com.att.tdp.issueflow.user.Role;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BootstrapAdminRunner implements ApplicationRunner {

    private final boolean enabled;
    private final String username;
    private final String email;
    private final String fullName;
    private final UserRepository userRepository;

    public BootstrapAdminRunner(
            @Value("${issueflow.bootstrap.admin.enabled:false}") boolean enabled,
            @Value("${issueflow.bootstrap.admin.username:}") String username,
            @Value("${issueflow.bootstrap.admin.email:}") String email,
            @Value("${issueflow.bootstrap.admin.full-name:}") String fullName,
            UserRepository userRepository
    ) {
        this.enabled = enabled;
        this.username = username;
        this.email = email;
        this.fullName = fullName;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        validateBootstrapProperties();
        if (userRepository.findByUsername(username.trim()).isPresent()) {
            return;
        }

        User user = new User();
        user.setUsername(username.trim());
        user.setEmail(email.trim());
        user.setFullName(fullName.trim());
        user.setRole(Role.ADMIN);
        user.setActive(true);
        userRepository.save(user);
    }

    private void validateBootstrapProperties() {
        if (username.isBlank() || email.isBlank() || fullName.isBlank()) {
            throw new IllegalStateException("Bootstrap admin requires username, email, and full-name");
        }
    }
}
