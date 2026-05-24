package com.att.tdp.issueflow.security.auth;

import com.att.tdp.issueflow.audit.AuditAction;
import com.att.tdp.issueflow.audit.AuditEventPublisher;
import com.att.tdp.issueflow.audit.AuditableEntityType;
import com.att.tdp.issueflow.user.Role;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import java.util.Optional;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BootstrapAdminRunner implements ApplicationRunner {

    static final String DEFAULT_ADMIN_USERNAME = "admin";
    static final String DEFAULT_ADMIN_EMAIL = "admin@issueflow.com";
    static final String DEFAULT_ADMIN_FULL_NAME = "System Admin";
    static final long DEFAULT_ADMIN_ID = 0L;

    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AuditEventPublisher auditEventPublisher;

    public BootstrapAdminRunner(
            UserRepository userRepository,
            JdbcTemplate jdbcTemplate,
            AuditEventPublisher auditEventPublisher
    ) {
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Optional<User> existingAdmin = userRepository.findByUsername(DEFAULT_ADMIN_USERNAME);
        if (existingAdmin.isPresent()) {
            if (existingAdmin.get().getId() != DEFAULT_ADMIN_ID) {
                throw new IllegalStateException("Bootstrap admin already exists with id "
                        + existingAdmin.get().getId()
                        + " but must use id " + DEFAULT_ADMIN_ID);
            }
            return;
        }
        userRepository.findById(DEFAULT_ADMIN_ID).ifPresent(existingUser -> {
            throw new IllegalStateException("Cannot create bootstrap admin because user id "
                    + DEFAULT_ADMIN_ID
                    + " already belongs to " + existingUser.getUsername());
        });

        jdbcTemplate.update("""
                        insert into users (
                            id,
                            version,
                            created_at,
                            updated_at,
                            created_by,
                            updated_by,
                            username,
                            email,
                            display_name,
                            role,
                            active
                        )
                        values (?, 0, now(), now(), ?, ?, ?, ?, ?, ?, true)
                        """,
                DEFAULT_ADMIN_ID,
                DEFAULT_ADMIN_ID,
                DEFAULT_ADMIN_ID,
                DEFAULT_ADMIN_USERNAME,
                DEFAULT_ADMIN_EMAIL,
                DEFAULT_ADMIN_FULL_NAME,
                Role.ADMIN.name()
        );

        User saved = userRepository.findById(DEFAULT_ADMIN_ID)
                .orElseThrow(() -> new IllegalStateException("Bootstrap admin was not created"));
        auditEventPublisher.systemAction(
                saved.getId(),
                AuditAction.CREATE,
                AuditableEntityType.USER,
                saved.getId(),
                null,
                new BootstrapAdminAuditValue(
                        saved.getId(),
                        saved.getUsername(),
                        saved.getEmail(),
                        saved.getFullName(),
                        saved.getRole()
                )
        );
    }

    private record BootstrapAdminAuditValue(Long id, String username, String email, String fullName, Role role) {
    }
}
