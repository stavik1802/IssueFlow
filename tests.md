# Testing IssueFlow

This guide explains how to create meaningful tests for IssueFlow and how to run the current test suite.



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


