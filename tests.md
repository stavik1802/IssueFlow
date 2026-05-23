# Testing IssueFlow

This guide explains how to create meaningful tests for IssueFlow and how to run the current test suite.

It is based on the current codebase and the assignment PDF:

```text
docs/requirements.pdf
```

The assignment requires relevant tests for the key behavior of the system: users, authentication, projects, tickets, comments, audit logs, dependencies, attachments, CSV import/export, auto-assignment, and escalation.

## Test Stack

The project currently uses:

- JUnit 5
- Mockito
- Spring Boot Test
- H2 in-memory database for tests

Configured test dependencies are in `pom.xml`:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-test</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>com.h2database</groupId>
  <artifactId>h2</artifactId>
  <scope>test</scope>
</dependency>
```

Testcontainers is not currently configured. It would be useful for PostgreSQL integration tests, but adding it requires new test dependencies and a new test setup.

## How to Run Tests

Run all tests:

```bash
./mvnw test
```

Using global Maven:

```powershell
mvn test
```

Run one test class:

```bash
./mvnw test -Dtest=UserServiceTest
```

Run several test classes:

```bash
./mvnw test -Dtest=TicketServiceTest,CommentServiceTest,TicketEscalationServiceTest
```

The test profile is controlled by:

```text
src/test/resources/application.yaml
```

The main application connects to PostgreSQL, but tests currently use H2:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:db;MODE=PostgreSQL;DB_CLOSE_ON_EXIT=FALSE
```

## Testing Style

Prefer focused service unit tests for business rules.

Use Mockito when the test should not touch the database:

- repository methods are mocked
- collaborators are mocked
- the service method is called directly
- assertions check returned DTOs, entity changes, and collaborator calls

Use Spring Boot integration tests when the behavior depends on framework wiring:

- security filters
- JWT request authentication
- transaction behavior
- JPA mappings
- repository queries
- real database constraints

Avoid tests for trivial getters and setters.

Use clear names:

```java
shouldCreateUserSuccessfully()
shouldRejectBackwardTicketStatusTransition()
shouldAutoAssignLeastLoadedDeveloper()
```

Keep tests deterministic:

- inject or mock `Clock` for time-based logic
- avoid real current time in assertions
- avoid relying on test execution order
- avoid external network dependencies

## Current Test Coverage

The current codebase already contains focused tests for most core behavior:

```text
src/test/java/com/att/tdp/issueflow/user/UserServiceTest.java
src/test/java/com/att/tdp/issueflow/security/auth/AuthServiceTest.java
src/test/java/com/att/tdp/issueflow/security/jwt/JwtTokenServiceTest.java
src/test/java/com/att/tdp/issueflow/security/jwt/TokenDenyListServiceTest.java
src/test/java/com/att/tdp/issueflow/project/ProjectServiceTest.java
src/test/java/com/att/tdp/issueflow/ticket/TicketServiceTest.java
src/test/java/com/att/tdp/issueflow/ticket/TicketDependencyServiceTest.java
src/test/java/com/att/tdp/issueflow/ticket/TicketAssignmentServiceTest.java
src/test/java/com/att/tdp/issueflow/comment/CommentServiceTest.java
src/test/java/com/att/tdp/issueflow/comment/MentionParserTest.java
src/test/java/com/att/tdp/issueflow/comment/MentionServiceTest.java
src/test/java/com/att/tdp/issueflow/audit/AuditLogServiceTest.java
src/test/java/com/att/tdp/issueflow/scheduler/TicketEscalationServiceTest.java
src/test/java/com/att/tdp/issueflow/attachment/AttachmentServiceTest.java
src/test/java/com/att/tdp/issueflow/attachment/LocalAttachmentStorageServiceTest.java
src/test/java/com/att/tdp/issueflow/importexport/TicketCsvServiceTest.java
src/test/java/com/att/tdp/issueflow/importexport/TicketImportServiceTest.java
```

## User Module Tests

Main class:

```text
UserServiceTest
```

Meaningful tests:

- create user success
- duplicate username rejected
- duplicate email rejected
- get user by id success
- get user not found
- update user full_name and role
- delete user
- passwordHash is never returned in `UserResponse`

Use Mockito for `UserRepository`. Do not use a real database for duplicate username/email tests; those are service decisions and can be tested by mocking `existsByUsername` and `existsByEmail`.

Example structure:

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock UserRepository userRepository;
    @Mock AuditEventPublisher auditEventPublisher;

    @Test
    void shouldRejectDuplicateUsername() {
        when(userRepository.existsByUsername("jdoe")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(ConflictException.class);

        verify(userRepository, never()).save(any());
    }
}
```

## Auth Module Tests

Main classes:

```text
AuthServiceTest
JwtTokenServiceTest
TokenDenyListServiceTest
```

Meaningful tests:

- login success
- login fails with bad password
- JWT token generation and validation
- logout deny-list blocks token reuse
- `/auth/me` returns current user

Use service unit tests for password and token behavior.

Use Spring Boot integration tests only if you want to verify the full HTTP security chain:

- unauthenticated protected request returns 401
- request with `Authorization: Bearer <token>` reaches controller
- logout denies token reuse through `JwtAuthenticationFilter`

## Project Module Tests

Main class:

```text
ProjectServiceTest
```

Meaningful tests:

- create project
- owner must exist
- update project
- soft delete hides project
- restore project
- list deleted projects

Mock `ProjectRepository` and `UserRepository` for service tests.

Use integration tests if you want to prove `@SQLDelete` and `@SQLRestriction` actually hide soft-deleted rows when JPA queries run.

## Ticket Module Tests

Main class:

```text
TicketServiceTest
```

Meaningful tests:

- create ticket
- get ticket by id
- update ticket
- status can only move forward: `TODO -> IN_PROGRESS -> IN_REVIEW -> DONE`
- backward status transition is rejected
- DONE ticket cannot be updated
- manual priority update clears `isOverdue`
- optimistic locking is configured with `@Version`

Keep status lifecycle checks focused. The production rule lives in `TicketLifecyclePolicy`, so tests should verify service behavior and policy behavior without rewriting the lifecycle in the test.

For optimistic locking, a unit test can assert that `AuditableEntity.version` has `@Version`. A deeper integration test can simulate two transactions updating the same row, but that is heavier and better suited to PostgreSQL/Testcontainers.

## Ticket Dependency Tests

Main class:

```text
TicketDependencyServiceTest
```

Meaningful tests:

- add dependency
- reject self-dependency
- reject duplicate dependency
- reject dependency across different projects
- unresolved blockers prevent transition to DONE

Use Mockito because most dependency rules are pure service logic over mocked tickets and repository answers.

## Comments and Mentions Tests

Main classes:

```text
CommentServiceTest
MentionParserTest
MentionServiceTest
```

Meaningful tests:

- create comment
- update comment
- delete comment
- parse `@username` mentions
- mentions are case-insensitive
- duplicate mentions ignored
- mentions are re-evaluated after comment update
- user mention lookup returns newest first
- optimistic locking is configured with `@Version`

Test parsing separately from persistence. `MentionParserTest` should not need Spring or Mockito.

Test mention synchronization with mocked repositories and users. This keeps the tests fast and shows exactly which mentions are created or removed.

## Audit Tests

Main class:

```text
AuditLogServiceTest
```

Meaningful tests:

- audit log created for important state-changing actions
- system actor is used for automatic actions
- audit logs can be filtered

For state-changing service tests, verify `AuditEventPublisher` is called. For audit filtering itself, use a Spring Boot test because filtering uses Spring Data JPA specifications.

Representative service audit tests should cover:

- ticket update
- comment update
- auto assignment
- escalation

Those prove both user-driven and system-driven audit paths.

## Auto-Assignment Tests

Main class:

```text
TicketAssignmentServiceTest
```

Meaningful tests:

- unassigned ticket is assigned to least-loaded `DEVELOPER`
- `ADMIN` users are excluded
- tie-break by oldest registered user
- if no developer exists, ticket remains unassigned
- manual assignee does not trigger auto-assignment
- auto assignment publishes a system audit event

Use mocked project members and mocked open-ticket counts. Do not create database rows just to test the selection algorithm.

## Scheduler and Escalation Tests

Main class:

```text
TicketEscalationServiceTest
```

Meaningful tests:

- overdue LOW becomes MEDIUM
- overdue MEDIUM becomes HIGH
- overdue HIGH becomes CRITICAL
- overdue CRITICAL sets `isOverdue` true
- DONE tickets are ignored
- tickets without dueDate are ignored
- escalation is idempotent
- escalation publishes a system audit event

Use a fixed `Clock`:

```java
Clock.fixed(Instant.parse("2026-05-20T10:15:30Z"), ZoneOffset.UTC)
```

This makes time-based tests deterministic.

## Attachment Tests

Main classes:

```text
AttachmentServiceTest
LocalAttachmentStorageServiceTest
```

Meaningful tests:

- valid file upload succeeds
- file over 10 MB is rejected
- invalid MIME type is rejected
- attachment metadata is saved
- path traversal is prevented
- generated stored filename is safe

Use `MockMultipartFile` for file uploads.

Use `@TempDir` for local storage tests so files are created under a temporary test folder and cleaned up automatically.

Do not use real user documents or hard-coded absolute upload paths in tests.

## CSV Import/Export Tests

Main classes:

```text
TicketCsvServiceTest
TicketImportServiceTest
```

Meaningful tests:

- export tickets to CSV
- import valid CSV
- CSV with commas and quotes works correctly
- invalid rows return errors
- partial success returns correct summary

Use Apache Commons CSV through `TicketCsvService` rather than hand-parsing strings in production. In tests, include values like:

```csv
id,title,description,status,priority,type,assigneeId
,"Fix login, then logout","Body with ""quotes"", and comma",TODO,HIGH,BUG,
```

This proves commas and quotes are handled correctly.

## Integration Tests Worth Adding Later

The current suite is mostly unit/service focused, which is the right default. Add integration tests only where the framework behavior matters.

Good candidates:

- `/auth/login`, `/auth/me`, and protected endpoints with `MockMvc`
- soft delete behavior with real JPA queries
- optimistic locking conflict with two transactions
- audit log filtering against a real database
- attachment upload endpoint with multipart request
- CSV import endpoint with multipart request

## Testcontainers Guidance

Testcontainers is not currently in `pom.xml`. If PostgreSQL-specific behavior becomes important, add Testcontainers for integration tests.

Useful PostgreSQL-specific cases:

- JSONB audit fields
- PostgreSQL timestamp behavior
- SQL constraints and indexes
- Hibernate behavior against the same database engine used in development

Keep Testcontainers tests separate from fast unit tests. A common pattern is:

```text
src/test/java/.../*IntegrationTest.java
```

Then run them intentionally:

```bash
./mvnw test -Dtest=*IntegrationTest
```

## Assumptions

- The assignment PDF is the functional contract, together with the README API table.
- Public API behavior should not be changed just to make testing easier.
- Existing unit tests should stay fast and deterministic.
- Mockito is appropriate for most service-level business rules.
- Spring Boot integration tests are valuable for security, JPA, and HTTP behavior.
- Testcontainers is useful but not currently configured, so it should be added only if PostgreSQL-specific integration coverage is required.
- Time-based behavior should use the existing `Clock` bean or a fixed `Clock` in tests.
