package com.att.tdp.issueflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.sql.init.mode=never",
        "spring.jpa.show-sql=false"
})
class PostgresMigrationIT {

    @Container
    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("issueflow_migration")
            .withUsername("issueflow")
            .withPassword("issueflow");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("issueflow.security.jwt.secret", () -> "migration-secret-key-that-is-long-enough-for-hmac-sha256");
    }

    @Test
    void startsFromCleanPostgresUsingMigrationsOnly() {
        Set<String> tables = Set.copyOf(jdbcTemplate.queryForList("""
                select table_name
                from information_schema.tables
                where table_schema = 'public'
                """, String.class));

        assertThat(tables)
                .contains(
                        "users",
                        "projects",
                        "project_members",
                        "tickets",
                        "ticket_dependencies",
                        "comments",
                        "mentions",
                        "attachments",
                        "audit_logs",
                        "flyway_schema_history"
                )
                .doesNotContain("task");

        Integer foreignKeys = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.table_constraints
                where constraint_schema = 'public'
                  and constraint_type = 'FOREIGN KEY'
                """, Integer.class);
        assertThat(foreignKeys).isNotNull().isGreaterThan(0);

        Integer activeUsernameIndexes = jdbcTemplate.queryForObject("""
                select count(*)
                from pg_indexes
                where schemaname = 'public'
                  and indexname = 'uk_users_username_active'
                """, Integer.class);
        assertThat(activeUsernameIndexes).isEqualTo(1);
    }
}
