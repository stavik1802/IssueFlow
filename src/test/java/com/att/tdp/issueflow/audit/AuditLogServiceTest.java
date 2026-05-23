package com.att.tdp.issueflow.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.att.tdp.issueflow.audit.dto.AuditLogFilter;
import com.att.tdp.issueflow.audit.dto.AuditLogResponse;
import com.att.tdp.issueflow.common.persistence.JpaAuditingConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AuditLogService.class, AuditEventPublisher.class, AuditLogServiceTest.TestConfig.class, JpaAuditingConfig.class})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AuditLogServiceTest {

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private AuditEventPublisher auditEventPublisher;

    @Test
    void createsAuditLog() {
        AuditLogResponse response = auditEventPublisher.userAction(
                1L,
                AuditAction.CREATE,
                AuditableEntityType.PROJECT,
                10L,
                null,
                new Value("IssueFlow")
        );

        assertThat(response.id()).isNotNull();
        assertThat(response.action()).isEqualTo(AuditAction.CREATE);
        assertThat(response.entityType()).isEqualTo(AuditableEntityType.PROJECT);
        assertThat(response.entityId()).isEqualTo(10L);
        assertThat(response.performedBy()).isEqualTo(1L);
        assertThat(response.actor()).isEqualTo(AuditActorType.USER);
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    void filtersByAction() {
        auditEventPublisher.userAction(1L, AuditAction.CREATE, AuditableEntityType.PROJECT, 10L, null, "created");
        auditEventPublisher.userAction(1L, AuditAction.DELETE, AuditableEntityType.PROJECT, 10L, "old", null);

        Page<AuditLogResponse> response = auditLogService.findLogs(
                new AuditLogFilter(null, null, null, AuditAction.DELETE, null, null, null, null),
                PageRequest.of(0, 10)
        );

        assertThat(response.getContent())
                .singleElement()
                .satisfies(log -> assertThat(log.action()).isEqualTo(AuditAction.DELETE));
    }

    @Test
    void filtersByEntity() {
        auditEventPublisher.userAction(1L, AuditAction.UPDATE, AuditableEntityType.USER, 20L, "old", "new");
        auditEventPublisher.userAction(1L, AuditAction.UPDATE, AuditableEntityType.PROJECT, 10L, "old", "new");

        Page<AuditLogResponse> response = auditLogService.findLogs(
                new AuditLogFilter(null, null, null, null, AuditableEntityType.PROJECT, 10L, null, null),
                PageRequest.of(0, 10)
        );

        assertThat(response.getContent())
                .singleElement()
                .satisfies(log -> {
                    assertThat(log.entityType()).isEqualTo(AuditableEntityType.PROJECT);
                    assertThat(log.entityId()).isEqualTo(10L);
                });
    }

    @Test
    void createsSystemActorLog() {
        AuditLogResponse response = auditEventPublisher.systemAction(
                AuditAction.AUTO_ESCALATE,
                AuditableEntityType.TICKET,
                33L,
                null,
                new Value("escalated")
        );

        assertThat(response.actor()).isEqualTo(AuditActorType.SYSTEM);
        assertThat(response.performedBy()).isNull();
    }

    @Test
    void controllerDoesNotExposeUpdateOrDeleteEndpoints() {
        boolean hasMutatingEndpoint = Arrays.stream(AuditController.class.getDeclaredMethods())
                .filter(method -> method.getDeclaringClass().equals(AuditController.class))
                .anyMatch(this::isMutatingEndpoint);

        assertThat(hasMutatingEndpoint).isFalse();
    }

    private boolean isMutatingEndpoint(Method method) {
        return method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(PutMapping.class)
                || method.isAnnotationPresent(PatchMapping.class)
                || method.isAnnotationPresent(DeleteMapping.class);
    }

    record Value(String name) {
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
