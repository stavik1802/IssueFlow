# Running IssueFlow

This guide is written for a fresh clone on any developer machine or CI runner. It does not rely on an IDE, a personal filesystem path, or a manually configured Java installation path.

## Prerequisites

Install:

- JDK 21
- Docker with Docker Compose
- Git

Verify Java:

```bash
java -version
```

The output must report Java 21. The project uses the Maven Wrapper, so a separate Maven installation is not required.

## Clone

```bash
git clone <repository-url>
cd issueflow-java
```

## Start PostgreSQL

The local database is provided by `compose.yml`.

```bash
docker compose up -d
docker compose ps
```

Default local database settings:

- Host: `localhost`
- Port: `5432`
- Database: `issueflow`
- Username: `issueflow`
- Password: `issueflow`

Stop the database:

```bash
docker compose down
```

## Build

Use the Maven Wrapper from the repository root:

```bash
./mvnw clean package
```

Windows:

```powershell
.\mvnw.cmd clean package
```

## Run Tests

Run the full test suite:

```bash
./mvnw clean test
```

Windows:

```powershell
.\mvnw.cmd clean test
```

Run only the PostgreSQL/Testcontainers verification:

```bash
./mvnw test -Dtest=PostgresMigrationIT,IssueFlowEndToEndIT
```

These tests require Docker. They are intentionally not skipped when Docker is unavailable, so CI failures expose missing PostgreSQL verification.

Run a single fast test class:

```bash
./mvnw test -Dtest=TicketServiceTest
```

## Run the Application

Start PostgreSQL first:

```bash
docker compose up -d
```

Start the API:

```bash
./mvnw spring-boot:run
```

Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

The API listens on:

```text
http://localhost:8080
```

## Configuration

Main configuration:

```text
src/main/resources/application.yaml
```

Important defaults:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/issueflow
    username: issueflow
    password: issueflow
  flyway:
    enabled: true
  jpa:
    hibernate:
      ddl-auto: validate

issueflow:
  bootstrap:
    admin:
      enabled: ${ISSUEFLOW_BOOTSTRAP_ADMIN_ENABLED:false}
  attachments:
    upload-directory: ${ISSUEFLOW_ATTACHMENT_UPLOAD_DIRECTORY:uploads/attachments}
  security:
    jwt:
      secret: ${ISSUEFLOW_JWT_SECRET:change-this-development-secret-to-at-least-32-bytes}
      expiration: ${ISSUEFLOW_JWT_EXPIRATION:1h}
```

For shared environments or CI, provide a strong `ISSUEFLOW_JWT_SECRET` through the environment or secret manager.

## Database Schema

Schema is managed by Flyway migrations in:

```text
src/main/resources/db/migration
```

Hibernate validates the schema at startup:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

The application does not use `schema.sql` or `data.sql` for normal startup.

## First Admin Bootstrap

`POST /auth/login` and `POST /users` are public. Other API endpoints require JWT authentication.

For a clean database, start the app once with bootstrap admin properties. The bootstrap runner creates the admin only if the username does not already exist.

Bash:

```bash
ISSUEFLOW_BOOTSTRAP_ADMIN_ENABLED=true \
ISSUEFLOW_BOOTSTRAP_ADMIN_USERNAME=admin \
ISSUEFLOW_BOOTSTRAP_ADMIN_EMAIL=admin@example.com \
ISSUEFLOW_BOOTSTRAP_ADMIN_FULL_NAME="IssueFlow Admin" \
ISSUEFLOW_BOOTSTRAP_ADMIN_PASSWORD=password123 \
./mvnw spring-boot:run
```

PowerShell:

```powershell
$env:ISSUEFLOW_BOOTSTRAP_ADMIN_ENABLED="true"
$env:ISSUEFLOW_BOOTSTRAP_ADMIN_USERNAME="admin"
$env:ISSUEFLOW_BOOTSTRAP_ADMIN_EMAIL="admin@example.com"
$env:ISSUEFLOW_BOOTSTRAP_ADMIN_FULL_NAME="IssueFlow Admin"
$env:ISSUEFLOW_BOOTSTRAP_ADMIN_PASSWORD="password123"
.\mvnw.cmd spring-boot:run
```

After the admin exists, disable bootstrap for normal runs by removing those environment variables or setting `ISSUEFLOW_BOOTSTRAP_ADMIN_ENABLED=false`.

## Manual API Smoke Test

Log in:

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password123"}'
```

Use the returned token:

```bash
TOKEN="<paste-token-here>"
```

Create a user:

```bash
curl -X POST http://localhost:8080/users \
  -H "Content-Type: application/json" \
  -d '{"username":"dev1","email":"dev1@example.com","fullName":"Dev One","role":"DEVELOPER","password":"password123"}'
```

Create a project:

```bash
curl -X POST http://localhost:8080/projects \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"IssueFlow API","description":"Backend project","ownerId":1}'
```

Create a ticket:

```bash
curl -X POST http://localhost:8080/tickets \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"projectId":1,"title":"Fix login bug","description":"Login fails on bad token","status":"TODO","type":"BUG","priority":"HIGH"}'
```

List tickets for a project:

```bash
curl "http://localhost:8080/tickets?projectId=1" \
  -H "Authorization: Bearer $TOKEN"
```

Upload an attachment:

```bash
curl -X POST http://localhost:8080/tickets/1/attachments \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@./docs/example.txt"
```

## Clean-Clone Verification Checklist

1. `java -version` reports JDK 21.
2. `docker compose up -d` starts PostgreSQL.
3. `./mvnw clean test` passes.
4. `./mvnw test -Dtest=PostgresMigrationIT,IssueFlowEndToEndIT` passes with Docker available.
5. `./mvnw spring-boot:run` starts the API.
6. Bootstrap admin can log in through `POST /auth/login`.
7. A protected endpoint succeeds with `Authorization: Bearer <token>`.

## Troubleshooting

### Java version is too old

Check:

```bash
java -version
```

Install JDK 21 and ensure it is first on your `PATH`. The Maven Wrapper uses the `java` executable visible in the shell.

### PostgreSQL is not reachable

Start the database:

```bash
docker compose up -d
docker compose ps
```

### Port 5432 is already in use

Stop the other local PostgreSQL process or change the published port in `compose.yml` and update `spring.datasource.url`.

### JWT secret is rejected

Use a secret long enough for HMAC SHA-256. For shared environments, inject it as `ISSUEFLOW_JWT_SECRET`.

### PostgreSQL tests cannot start

Start Docker and rerun:

```bash
./mvnw test -Dtest=PostgresMigrationIT,IssueFlowEndToEndIT
```
