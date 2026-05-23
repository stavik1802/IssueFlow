# Prompts

In this md file I will show my main prompts used to interact with AI

## Model

For my model I've used GPT 5.5 model by OPENAI, using Codex.

## Main Prompts

## Step 1 Implementation prompts

### 1. Creating foundation/common modules

 You are a senior Java backend engineer working on a production-quality Spring Boot backend called IssueFlow.

Your task is to implement ONLY the foundation/common infrastructure layer.

Tech Stack:
- Java 21
- Spring Boot 3
- Maven
- PostgreSQL
- Spring Data JPA
- Hibernate
- Jakarta Validation
- Lombok allowed

IMPORTANT CONSTRAINTS:
- Follow the existing architecture exactly.
- Do NOT implement business modules (users/projects/tickets/comments/etc).
- Do NOT create placeholder code for future modules.
- Do NOT modify pom.xml unless absolutely necessary.
- Keep controllers thin.
- Use constructor injection only.
- Use production-quality code.
- Prefer immutability where appropriate.
- Use clean exception handling.
- Add JavaDoc only where truly useful.

Target package structure:

com.att.tdp.issueflow
├── common
│   ├── api
│   │   ├── ApiError.java
│   │   ├── FieldErrorDetail.java
│   │   └── PageResponse.java
│   ├── exception
│   │   ├── GlobalExceptionHandler.java
│   │   ├── NotFoundException.java
│   │   ├── ConflictException.java
│   │   ├── ForbiddenException.java
│   │   ├── BadRequestException.java
│   │   └── BusinessRuleViolationException.java
│   ├── persistence
│   │   ├── AuditableEntity.java
│   │   ├── SoftDeletable.java
│   │   └── JpaAuditingConfig.java
│   └── time
│       └── ClockConfig.java

Requirements:

### 1. ApiError
Implement a standardized API error response object.

Include:
- timestamp
- status
- error
- message
- path
- fieldErrors (optional)

### 2. FieldErrorDetail
Represents validation errors.

Include:
- field
- message
- rejectedValue

### 3. PageResponse<T>
Generic pagination wrapper.

Include:
- content
- page
- size
- totalElements
- totalPages
- last

### 4. Custom Exceptions
Implement:
- NotFoundException
- ConflictException
- ForbiddenException
- BadRequestException
- BusinessRuleViolationException

Requirements:
- clean constructors
- meaningful inheritance hierarchy
- no unnecessary complexity

### 5. GlobalExceptionHandler
Implement using @RestControllerAdvice.

Handle:
- validation errors
- MethodArgumentNotValidException
- ConstraintViolationException
- custom business exceptions
- generic exceptions

Requirements:
- return consistent ApiError format
- map proper HTTP status codes
- aggregate field validation errors correctly
- avoid leaking internal stack traces

### 6. AuditableEntity
Implement as @MappedSuperclass.

Include:
- createdAt
- updatedAt

Requirements:
- use Spring Data auditing
- use Instant
- automatically populated

### 7. SoftDeletable
Design reusable soft delete support.

Include:
- deleted
- deletedAt

Requirements:
- reusable across Project and Ticket later
- avoid overengineering
- explain tradeoffs briefly in comments if needed

### 8. JpaAuditingConfig
Enable JPA auditing correctly.

### 9. ClockConfig
Expose a Clock bean for testable time handling.

Requirements:
- use UTC
- support deterministic tests later

### 10. Quality Requirements
- Use proper access modifiers
- Avoid field injection
- Use modern Spring Boot conventions
- Avoid unnecessary annotations
- Avoid circular dependencies
- Ensure code compiles cleanly

### 11. Testing
Generate unit tests ONLY for:
- GlobalExceptionHandler
- PageResponse if useful

Do NOT generate excessive boilerplate tests.

### 12. Deliverables
Generate:
- all Java files
- imports
- package declarations
- tests
- concise explanations for important design decisions

Do NOT:
- implement security
- implement repositories
- implement services
- implement controllers outside exception handling
- implement entities for business modules
- implement JWT/auth

### 2. Creating USER module
You are a senior Java backend engineer working on a Spring Boot backend called IssueFlow.

Your task is to implement ONLY the User module.

Tech Stack:
- Java 21
- Spring Boot 3
- Maven
- Spring Data JPA
- Hibernate
- PostgreSQL
- Jakarta Validation
- Lombok allowed

Existing architecture:
com.att.tdp.issueflow
├── common
│   ├── api
│   ├── exception
│   ├── persistence
│   └── time
└── user
    ├── User.java
    ├── Role.java
    ├── UserRepository.java
    ├── UserService.java
    ├── UserController.java
    ├── UserMapper.java
    └── dto
        ├── CreateUserRequest.java
        ├── UpdateUserRequest.java
        └── UserResponse.java

IMPORTANT CONSTRAINTS:
- Implement ONLY the user package.
- Do NOT implement auth/security yet.
- Do NOT implement projects/tickets/comments.
- Do NOT expose JPA entities directly from controllers.
- Controllers must be thin.
- Business logic must be in UserService.
- Use constructor injection only.
- Use DTOs for all request/response bodies.
- Use the existing common exceptions and ApiError structure.
- Use @Transactional on service methods where appropriate.
- Use Spring Data JPA repositories.
- Code must compile cleanly.

Functional Requirements:
- Register/create a new user with:
  - username
  - email
  - fullName
  - role: ADMIN or DEVELOPER
- Fetch user by id.
- Fetch all users.
- Update user details:
  - fullName
  - role
- Delete user.

Validation:
- username is required, non-blank, reasonable length.
- email is required and valid.
- fullName is required, non-blank.
- role must be ADMIN or DEVELOPER.
- username must be unique.
- email must be unique.

Entity Requirements:
- User entity should extend or use the existing AuditableEntity if available.
- Fields:
  - id
  - username
  - email
  - fullName
  - role
- Use @Enumerated(EnumType.STRING) for Role.
- Add unique constraints for username and email.
- Do not store raw passwords.

Endpoints:
- POST /users
- GET /users/{id}
- GET /users
- PATCH /users/{id}
- DELETE /users/{id}

Response:
- Return UserResponse only.
- Never return passwordHash.

Errors:
- If user not found, throw NotFoundException.
- If username/email already exists, throw ConflictException.
- If invalid input, rely on validation and GlobalExceptionHandler.

Mapper:
- Implement UserMapper.
- Keep mapping simple and explicit.
- Do not use MapStruct unless already configured.

Testing:
Generate tests for:
- create user success
- duplicate username/email
- get user not found
- update user role/fullName
- delete user
- passwordHash not exposed in response

Use JUnit 5 and Mockito for service tests.
Do not create excessive boilerplate.

Deliverables:
- User.java
- Role.java
- UserRepository.java
- UserService.java
- UserController.java
- UserMapper.java
- DTO classes
- relevant unit tests

Do NOT:
- implement JWT
- implement login
- implement CurrentUser
- implement project membership
- implement admin authorization yet
- modify unrelated packages

### 3.  Auth module
You are a senior Java backend engineer working on IssueFlow.

Implement ONLY the Security/Auth module.

Assume the User module already exists with:
- User
- Role
- UserRepository
- UserService

Tech stack:
- Java 21
- Spring Boot 3
- Spring Security
- JWT
- Maven

Requirements:
1. Implement JWT authentication.
2. Implement POST /auth/login.
3. Implement POST /auth/logout.
4. Implement GET /auth/me.
5. Protect all API endpoints by default.
6. Allow unauthenticated access only to:
   - POST /auth/login
   - POST /users
   - Swagger endpoints if present
7. Use constructor injection only.
8. Do not modify business modules except if absolutely required.

Files to implement:
- SecurityConfig
- JwtAuthenticationFilter
- JwtTokenService
- TokenDenyListService
- AuthController
- AuthService
- LoginRequest
- LoginResponse
- CurrentUser
- PasswordConfig

JWT:
- token should contain user id as subject
- include username and role as claims
- configurable expiration
- configurable secret from application properties

Logout:
- implement simple server-side deny-list service
- rejected tokens should not authenticate again

AuthService:
- login validates username/password
- throws Unauthorized or BadRequest style exception on invalid credentials
- me returns current user profile

Testing:
Add unit tests for:
- successful login
- failed login wrong password
- token generation/validation
- logout deny-list behavior

Constraints:
- Do not implement projects/tickets/comments.
- Do not place business logic in controllers.
- Use existing common exception/error handling.
- Code must compile cleanly.

### 4. Project modules
You are a senior Java Spring Boot engineer.

Implement ONLY the Project module.

Packages:
project

Files:
- Project.java
- ProjectMember.java
- ProjectRepository.java
- ProjectMemberRepository.java
- ProjectService.java
- WorkloadService.java
- ProjectController.java
- ProjectMapper.java
- dto/CreateProjectRequest.java
- dto/UpdateProjectRequest.java
- dto/ProjectResponse.java
- dto/WorkloadResponse.java

Requirements:
- Create project with name, description, ownerId
- Get project by id
- Get all non-deleted projects
- Update project name/description
- Soft delete project
- Restore project, ADMIN only if security is available
- List deleted projects, ADMIN only if security is available
- Project has owner User
- Project supports members through ProjectMember
- Workload endpoint:
  GET /projects/{projectId}/workload

Validation:
- name required
- ownerId must exist
- deleted projects hidden from normal responses

Architecture:
- Thin controller
- Business logic in service
- DTO/entity separation
- Use @Transactional in service
- Use existing common exceptions
- Use AuditableEntity and SoftDeletable if available

Tests:
- create project
- owner not found
- get project
- update project
- soft delete hides project
- restore project

Do NOT:
- implement tickets yet
- implement comments
- implement audit unless hook method is needed

### 5. Ticket core
You are a senior Java Spring Boot engineer.

Implement ONLY the Ticket Core module.

Packages:
ticket

Files:
- Ticket.java
- TicketStatus.java
- TicketPriority.java
- TicketType.java
- TicketRepository.java
- TicketService.java
- TicketLifecyclePolicy.java
- TicketController.java
- TicketMapper.java
- dto/CreateTicketRequest.java
- dto/UpdateTicketRequest.java
- dto/TicketResponse.java

Requirements:
- Create ticket with:
  title, description, status, priority, type, projectId, optional assigneeId, optional dueDate
- Get ticket by id
- Get all tickets by project
- Update ticket fields:
  title, description, status, priority, assigneeId, dueDate
- Soft delete ticket
- Restore ticket, ADMIN only if security is available
- List deleted tickets by project, ADMIN only if security is available

Business rules:
- Status enum:
  TODO, IN_PROGRESS, IN_REVIEW, DONE
- Priority enum:
  LOW, MEDIUM, HIGH, CRITICAL
- Type enum:
  BUG, FEATURE, TECHNICAL
- Status can only move forward:
  TODO -> IN_PROGRESS -> IN_REVIEW -> DONE
- Backward transitions forbidden
- DONE ticket cannot be updated
- Manual priority change clears isOverdue
- 2 or more users can't update ticket at the same time.
- Normal queries hide deleted tickets

Architecture:
- Use TicketLifecyclePolicy for lifecycle validation
- Thin controller
- Business logic in TicketService
- DTO/entity separation
- Use @Transactional
- Use existing common exceptions

Tests:
- create ticket
- get by id
- update success
- backward status transition rejected
- DONE ticket update rejected
- soft delete hides ticket
- 2 or more users try update ticket at the same time.

Do NOT:
- implement dependencies yet
- implement comments
- implement CSV
- implement scheduler

### 6. Ticket dipendency Module
You are a senior Java Spring Boot engineer.

Implement ONLY the Ticket Dependency module.

Packages:
ticket

Files:
- TicketDependency.java
- TicketDependencyRepository.java
- TicketDependencyService.java
- dto/AddDependencyRequest.java
- dto/DependencyResponse.java

You may update TicketController only to add dependency endpoints.

Endpoints:
- POST /tickets/{ticketId}/dependencies
- GET /tickets/{ticketId}/dependencies
- DELETE /tickets/{ticketId}/dependencies/{blockerId}

Requirements:
- Body { "blockedBy": 42 } means ticketId is blocked by ticket 42
- Both tickets must exist
- Both tickets must belong to the same project
- Cannot add duplicate dependency
- Cannot add self-dependency
- Prevent ticket from moving to DONE if unresolved blockers exist
- prevent dependency cycles

Architecture:
- Business logic in TicketDependencyService
- Use @Transactional
- Use common exceptions
- Keep controller thin

Tests:
- add dependency success
- reject missing ticket
- reject different project
- reject self-dependency
- reject duplicate
- prevent DONE if blocker not DONE

Do NOT:
- rewrite TicketService except minimal integration for DONE blocking

7.Common and Mention Module
You are a senior Java Spring Boot engineer.

Implement ONLY the Comment and Mention module.

Packages:
comment

Files:
- Comment.java
- CommentRepository.java
- CommentService.java
- CommentController.java
- Mention.java
- MentionRepository.java
- MentionService.java
- MentionParser.java
- CommentMapper.java
- dto/CreateCommentRequest.java
- dto/UpdateCommentRequest.java
- dto/CommentResponse.java
- dto/MentionedUserResponse.java

Requirements:
- Add comment to ticket with content and authorId
- Get all comments for ticket
- Update comment content
- Delete comment
- Don't let two users change the same comment
- Parse @username mentions from comment content
- Mentions are case-insensitive
- Ignore duplicate mentions in same comment
- Ignore nonexistent usernames
- On comment update, re-evaluate mentions:
  - add new mentions
  - remove deleted mentions
- GET /users/{userId}/mentions returns comments where user was mentioned, newest first
- Comment response includes mentionedUsers

Architecture:
- MentionParser only parses usernames
- MentionService handles persistence
- CommentService coordinates comment + mentions
- Thin controllers
- DTO/entity separation
- Use @Transactional

Tests:
- create comment
- update comment
- delete comment
- parse mentions
- case-insensitive mention matching
- update removes old mentions
- user mentions newest first

Do NOT:
- implement tickets
- implement audit unless only publishing hooks

### 8. Audit module
You are a senior Java Spring Boot engineer.

Implement ONLY the Audit module.

Packages:
audit

Files:
- AuditLog.java
- AuditAction.java
- AuditActorType.java
- AuditableEntityType.java
- AuditLogRepository.java
- AuditLogService.java
- AuditEventPublisher.java
- AuditController.java
- dto/AuditLogFilter.java
- dto/AuditLogResponse.java

Requirements:
- Persistent append-only audit log
- Log all state-changing actions
- Support actor USER and SYSTEM
- Store:
  actorType, actorId, action, entityType, entityId, oldValue, newValue, createdAt
- Provide endpoint to retrieve all logs
- Support filtering by fields:
  actorType, actorId, action, entityType, entityId, date range
- Use JSON string for oldValue/newValue if simple
- Do not allow updating/deleting audit logs

Architecture:
- AuditLogService writes logs
- AuditEventPublisher provides clean API for other services
- Controller is read-only
- Use pagination if PageResponse exists
- Use @Transactional

Tests:
- create audit log
- filter by action
- filter by entity
- system actor log
- append-only behavior by not exposing update/delete

Do NOT:
- modify every module to add audit yet
- implement scheduler
- implement auto-assignment

### 9. Auto-assignment / workload module
You are a senior Java Spring Boot engineer.

Implement ONLY auto-assignment and workload behavior.

Packages:
ticket and project

Files:
- ticket/TicketAssignmentService.java
- project/WorkloadService.java

You may minimally update TicketService create flow.

Requirements:
- When ticket is created without assigneeId:
  - find DEVELOPER users linked to the project
  - count non-DONE tickets assigned to each developer in same project
  - choose user with lowest openTicketCount
  - tie-break by oldest registered user
  - if no developer linked to project, leave assignee null
- Auto-assignment happens only on ticket creation
- PATCH /tickets/{id} can override assigneeId manually
- GET /projects/{projectId}/workload returns:
  userId, username, openTicketCount
- Sorted by openTicketCount ascending
- Exclude ADMIN users
- Record AUTO_ASSIGN audit log with actor SYSTEM if Audit module exists

Architecture:
- TicketAssignmentService chooses assignee
- WorkloadService calculates workload
- Use repositories efficiently
- Avoid N+1 queries
- Use @Transactional where needed

Tests:
- assign least loaded developer
- tie-break oldest user
- exclude admin
- no developer leaves unassigned
- manual assignee does not trigger auto assignment

Do NOT:
- rewrite ticket CRUD

### 9. Scheduler Module
You are a senior Java Spring Boot engineer.

Implement ONLY the Ticket Escalation Scheduler module.

Packages:
scheduler

Files:
- TicketEscalationScheduler.java
- TicketEscalationService.java

You may minimally update TicketRepository if needed.

Requirements:
- Ticket has optional dueDate
- Scheduled job finds overdue unresolved tickets
- Escalate priority:
  LOW -> MEDIUM
  MEDIUM -> HIGH
  HIGH -> CRITICAL
- If already CRITICAL and overdue, set isOverdue = true
- Escalation is idempotent
- DONE tickets should not be escalated
- Tickets without dueDate ignored
- Manual priority change clears isOverdue and resets escalation state
- Escalation does not change status
- Audit each automatic escalation with actor SYSTEM if Audit module exists

Architecture:
- Use @Scheduled
- Use Clock bean from common/time if available
- Main logic in TicketEscalationService
- Scheduler only triggers service
- Use @Transactional
- Avoid loading unnecessary data

Tests:
- LOW escalates to MEDIUM
- HIGH escalates to CRITICAL
- CRITICAL overdue sets isOverdue
- DONE ignored
- no dueDate ignored
- idempotency

Do NOT:
- implement ticket CRUD

### 10. Attachment module
You are a senior Java Spring Boot engineer.

Implement ONLY the Attachment module.

Packages:
attachment

Files:
- Attachment.java
- AttachmentRepository.java
- AttachmentService.java
- AttachmentStorageService.java
- LocalAttachmentStorageService.java
- AttachmentController.java
- dto/AttachmentResponse.java

Requirements:
- Attach files to tickets
- Validate max file size: 10 MB
- Allowed MIME types:
  image/png
  image/jpeg
  application/pdf
  text/plain
- Reject all other types
- Store metadata:
  id, ticketId, originalFilename, storedFilename, mimeType, size, path, createdAt
- Store files locally under configurable upload directory
- Use safe generated filenames
- Prevent path traversal
- Return AttachmentResponse
- Audit upload/delete if Audit module exists

Possible endpoints:
- POST /tickets/{ticketId}/attachments
- GET /tickets/{ticketId}/attachments
- GET /attachments/{id}/download
- DELETE /attachments/{id}

Architecture:
- AttachmentStorageService interface
- LocalAttachmentStorageService implementation
- AttachmentService coordinates DB + storage
- Use @Transactional carefully
- Keep controller thin

Tests:
- upload valid file
- reject too-large file
- reject invalid MIME type
- list ticket attachments
- safe filename generation

Do NOT:
- implement S3
- modify ticket logic except verifying ticket exists

### 11. Import/export module
You are a senior Java Spring Boot engineer.

Implement ONLY the Ticket CSV Import/Export module.

Packages:
importexport

Files:
- TicketCsvService.java
- TicketImportService.java
- TicketExportService.java
- dto/TicketImportSummary.java
- dto/TicketImportError.java

You may add endpoints to TicketController or create dedicated controller if cleaner.

Requirements:
- Export:
  GET /tickets/export?projectId={id}
  Returns CSV file with:
  id, title, description, status, priority, type, assigneeId
- Import:
  POST /tickets/import
  multipart/form-data CSV file
  form field projectId
  Creates tickets in bulk
  Returns:
  { created, failed, errors }
- CSV must correctly handle commas and quotes
- Use Apache Commons CSV or similar
- Validate each row independently
- Partial success allowed
- Invalid rows should not stop full import
- assigneeId optional
- status/priority/type must be valid enums
- projectId must exist
- Audit import/export if Audit module exists

Architecture:
- TicketCsvService parses/generates CSV
- TicketImportService handles validation and creation
- TicketExportService handles export
- Use @Transactional carefully
- Avoid exposing entities

Tests:
- export valid CSV
- import valid CSV
- import with quoted commas
- import invalid enum
- partial failure summary

Do NOT:
- rewrite ticket CRUD

### 12. Audit wiring module
You are a senior Java Spring Boot engineer.

Your task is final integration wiring for IssueFlow.

Scope:
- Add audit logging calls to existing state-changing services.
- Do not rewrite business logic.
- Do not change public API behavior.

State-changing actions to audit:
- user create/update/delete
- project create/update/soft-delete/restore
- ticket create/update/soft-delete/restore
- comment create/update/delete
- dependency add/remove
- attachment upload/delete
- CSV import
- auto assignment
- auto escalation

Requirements:
- Use AuditEventPublisher or AuditLogService
- actor should be current user when available
- actor should be SYSTEM for scheduler/auto-assignment where appropriate
- Include entity type, entity id, action, oldValue/newValue when practical
- Avoid breaking tests
- Keep services readable

Tests:
- verify audit called for representative actions:
  ticket update
  comment update
  auto assignment
  escalation

Do NOT:
- redesign audit module
- change entity schema unless necessary

### 13. Final Review and Test
You are a senior Java test engineer.

Review the IssueFlow project and add missing tests.

Focus on business-critical behavior, not boilerplate.

Must cover:
- auth login success/failure
- user CRUD validation
- project soft delete and restore
- ticket lifecycle forward-only transitions
- DONE ticket immutable
- optimistic locking on Ticket and Comment
- unresolved dependencies block DONE
- comment mention parsing and re-evaluation
- auto-assignment least-loaded developer
- auto-escalation priority rules
- CSV import/export with commas and quotes
- attachment MIME and size validation
- audit log creation

Use:
- JUnit 5
- Mockito for unit tests
- SpringBootTest/Testcontainers only where integration is valuable

Constraints:
- Do not rewrite production code unless required to make it testable
- Prefer focused tests
- Avoid excessive brittle tests

## Step 2  - review project against reqiurements

You are a senior staff-level Java backend reviewer performing a production-quality implementation audit for the IssueFlow backend system.

Your task is to REVIEW the existing codebase against the attached PDF requirements document and determine:

1. What is fully implemented correctly
2. What is partially implemented
3. What is missing entirely
4. What is implemented incorrectly
5. What business rules are violated
6. What edge cases are missing
7. What security issues exist
8. What concurrency/data consistency problems exist
9. What tests are missing
10. What architectural/code quality problems exist

IMPORTANT:
- DO NOT implement new features unless explicitly necessary for demonstrating a problem.
- This is primarily a REVIEW and GAP ANALYSIS task.
- Be extremely strict and thorough.
- Assume this code will be reviewed by senior backend engineers.
- Compare behavior directly against the PDF requirements.
- Verify BOTH functionality and correctness.
- Verify BOTH implementation and tests.
- Do not assume something works unless the code clearly proves it.

Inputs:
- The attached PDF contains the official IssueFlow requirements.
- The repository contains the Spring Boot implementation.

Tech stack:
- Java 21
- Spring Boot 3
- PostgreSQL
- Spring Security
- JPA/Hibernate
- JWT
- Maven

Review Scope:
- Users
- Authentication
- Projects
- Tickets
- Ticket lifecycle
- Comments
- Mentions
- Audit logs
- Ticket dependencies
- Attachments
- CSV import/export
- Soft delete
- Scheduler/escalation
- Auto assignment
- Validation
- Security
- Tests
- Transactions
- Error handling
- DTO/entity separation
- Architecture quality

For EACH requirement in the PDF:

Determine:
- FULLY IMPLEMENTED
- PARTIALLY IMPLEMENTED
- MISSING
- INCORRECTLY IMPLEMENTED

Then explain WHY.

VERY IMPORTANT REVIEW AREAS:

### 1. Security
Check:
- JWT validation
- endpoint protection
- role-based authorization
- token invalidation
- exposed sensitive fields
- improper trust of request body IDs

### 2. Concurrency
Check:
- simultaneous ticket update protection
- simultaneous comment update protection
- transaction boundaries
- race conditions

### 3. Ticket Lifecycle Rules
Verify:
- TODO -> IN_PROGRESS -> IN_REVIEW -> DONE only
- no backward transitions
- DONE immutable
- dependency blockers prevent DONE

### 4. Soft Delete
Verify:
- records are not physically deleted
- deleted records hidden from normal queries
- restore endpoints work
- deleted listings work

### 5. Auto Assignment
Verify:
- least-loaded DEVELOPER selected
- ADMIN excluded
- tie-break by oldest registration
- only non-DONE tickets counted

### 6. Escalation Scheduler
Verify:
- dueDate behavior
- escalation order
- CRITICAL handling
- idempotency
- DONE ignored

### 7. Mentions
Verify:
- case-insensitive matching
- mention re-evaluation on edit
- duplicate handling
- mention retrieval ordering

### 8. CSV Import/Export
Verify:
- commas/quotes handled correctly
- partial failures handled
- validation errors returned

### 9. Attachments
Verify:
- MIME validation
- size validation
- path traversal prevention
- safe storage

### 10. Audit Logging
Verify:
- all state-changing actions logged
- SYSTEM actor handled
- append-only behavior
- filtering support

### 11. Architecture Review
Check:
- thin controllers
- service-layer business logic
- DTO/entity separation
- repository usage
- circular dependencies
- N+1 query risks
- fetch strategies
- transaction usage
- exception handling consistency

### 12. Test Coverage Review
Check:
- critical business rules tested
- happy paths only vs edge cases
- missing concurrency tests
- missing security tests
- missing integration tests

Output Format:

### 1. Executive Summary
- Overall quality score (1-10)
- Production readiness assessment
- Biggest risks
- Strongest parts

### 2. Requirement-by-Requirement Audit
For each requirement:
- Requirement
- Status:
  - FULLY IMPLEMENTED
  - PARTIALLY IMPLEMENTED
  - MISSING
  - INCORRECT
- Evidence from code
- Problems found
- Recommended fix

### 3. Security Findings
List all security issues.

### 4. Concurrency/Data Integrity Findings
List all race conditions and consistency risks.

### 5. Architecture Findings
List architecture/code quality issues.

### 6. Testing Findings
List missing or weak tests.

### 7. Missing Features
List anything absent from implementation.

### 8. Final Recommendations
Prioritized fixes:
- Critical
- High
- Medium
- Low

Review style:
- Be strict and detailed.
- Prefer concrete examples from code.
- Explain exactly why something is wrong.
- Do not give generic advice.
- Think like a senior backend reviewer evaluating a take-home assignment.

## Step 3 Integration Test
You are a senior Java Spring Boot integration-test engineer.

I want you to create a REAL end-to-end integration test for the IssueFlow backend.

VERY IMPORTANT:
- Do NOT use Mockito.
- Do NOT mock repositories.
- Do NOT mock services.
- Do NOT mock security.
- Do NOT mock the database.
- Do NOT use @MockBean.
- Do NOT use unit tests.
- Use the real Spring application context.
- Use the real controllers, services, repositories, security filters, JPA/Hibernate, and PostgreSQL.
- This test should verify the full system works together.

Test style:
- Use @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
- Use TestRestTemplate OR MockMvc with real Spring Security filters.
- Prefer Testcontainers PostgreSQL if available.
- If Testcontainers is not configured, add it properly.
- Use a real PostgreSQL container.
- Use real HTTP requests.
- Use JWT login flow to authenticate requests.
- Do not bypass authentication by calling services directly.

Goal:
Create one large integration test class that walks through the complete IssueFlow workflow and verifies all modules.

Suggested class name:
IssueFlowEndToEndIT

The test should cover:

1. Application startup
- Spring context loads
- PostgreSQL container starts
- database schema is created/migrated

2. User module
- create ADMIN user
- create DEVELOPER user A
- create DEVELOPER user B
- verify duplicate username/email is rejected
- fetch user by id
- fetch all users
- update user fullName/role
- delete user if API supports it

3. Authentication
- login as ADMIN
- login as DEVELOPER
- verify invalid login fails
- verify protected endpoint without JWT fails
- verify /auth/me returns current user
- verify logout invalidates token if implemented

4. Project module
- create project with ADMIN or owner
- fetch project by id
- fetch all projects
- update project name/description
- verify invalid ownerId is rejected

5. Project membership / workload
- add developers to project if membership endpoint exists
- if no membership endpoint exists, create project members directly only if that is how the application is designed
- call GET /projects/{projectId}/workload
- verify users are sorted by openTicketCount ascending

6. Ticket module
- create ticket with manual assignee
- create ticket without assignee and verify auto-assignment chooses least-loaded DEVELOPER
- fetch ticket by id
- fetch tickets by project
- update title/description
- update priority
- update status forward:
  TODO -> IN_PROGRESS -> IN_REVIEW -> DONE
- verify backward transition is rejected
- verify DONE ticket cannot be updated
- verify manual priority update clears isOverdue
- verify manual assignee override works
- verify invalid projectId is rejected
- verify invalid assigneeId is rejected

7. Ticket optimistic locking / simultaneous update protection
- create ticket
- fetch same ticket twice
- update it once with version/If-Match, depending on current API design
- attempt second update with stale version/If-Match
- verify HTTP 409 Conflict
- If current API does not expose version/If-Match, add a failing test that clearly demonstrates the requirement gap

8. Ticket dependencies
- create blocker ticket
- create blocked ticket
- add dependency:
  POST /tickets/{ticketId}/dependencies
- list dependencies
- verify duplicate dependency is rejected
- verify self-dependency is rejected
- verify dependency across projects is rejected
- verify blocked ticket cannot transition to DONE while blocker is unresolved
- mark blocker DONE
- verify blocked ticket can now transition to DONE
- remove dependency

9. Comment module
- create comment on ticket
- fetch comments for ticket
- update comment content
- delete comment
- verify invalid ticketId is rejected
- verify invalid authorId is rejected

10. Comment optimistic locking / simultaneous edit protection
- create comment
- fetch same comment twice if API exposes version
- update once
- attempt stale update
- verify HTTP 409 Conflict
- If current API does not expose version/If-Match, add a failing test that clearly demonstrates the requirement gap

11. Mentions
- create comment containing @developerA and @developerB with different casing
- verify mentionedUsers appears in comment response
- verify duplicate mentions are ignored
- call GET /users/{userId}/mentions
- verify newest-first ordering
- update comment to remove one mention and add another
- verify mention list is re-evaluated correctly

12. Attachments
- upload valid text/plain file
- upload valid image/png if simple test fixture is easy
- verify file over 10MB is rejected
- verify invalid MIME type is rejected
- list attachments for ticket
- download attachment if endpoint exists
- delete attachment if endpoint exists

13. CSV export/import
- create several tickets with descriptions containing commas and quotes
- export tickets:
  GET /tickets/export?projectId={id}
- verify CSV contains:
  id, title, description, status, priority, type, assigneeId
- verify commas and quotes are escaped correctly
- import valid CSV with quoted commas
- verify created count
- import CSV with invalid rows
- verify failed count and errors array

14. Soft delete
- soft delete ticket
- verify normal GET/list does not return deleted ticket
- verify GET /tickets/deleted?projectId={id} returns deleted ticket and requires ADMIN
- restore ticket
- verify normal GET returns ticket again
- soft delete project
- verify normal GET/list does not return deleted project
- verify GET /projects/deleted returns deleted project and requires ADMIN
- restore project
- verify normal GET returns project again

15. Audit log
- verify audit logs exist for representative state-changing actions:
  user create/update/delete
  project create/update/delete/restore
  ticket create/update/delete/restore
  comment create/update/delete
  dependency add/remove
  attachment upload/delete
  CSV import
  auto assignment
  auto escalation
- verify AUTO_ASSIGN has actor SYSTEM and action AUTO_ASSIGN
- verify audit filtering works by action/entity/actor/date if endpoint supports it

16. Scheduler / escalation
- create ticket with dueDate in the past and priority LOW
- trigger escalation logic through real endpoint only if endpoint exists
- If no endpoint exists, call scheduler/service only as a last resort, but do NOT mock anything
- verify LOW -> MEDIUM
- run again and verify MEDIUM -> HIGH
- run again and verify HIGH -> CRITICAL
- run again and verify CRITICAL remains CRITICAL and isOverdue=true
- verify DONE tickets are not escalated
- verify tickets without dueDate are ignored
- verify status does not change

17. Validation and error handling
- send invalid enum values
- send missing required fields
- send malformed JSON
- verify informative 400 errors
- verify not found returns 404
- verify business rule violations return 400 or 409 according to project convention

Technical requirements:
- Use real JSON serialization/deserialization.
- Use helper methods only for readability:
  createUser()
  login()
  authHeaders()
  createProject()
  createTicket()
  createComment()
- Clean database between test runs.
- Use @Sql, repository cleanup, or transaction cleanup.
- Avoid relying on test execution order unless this is intentionally one single @Test workflow.
- If it is one huge workflow test, make it readable with private helper methods and clear section comments.
- Do not make assertions vague. Assert exact statuses and important response fields.
- Do not ignore response bodies.
- Use AssertJ assertions.

Important constraints:
- Do not change production code unless required to expose missing required behavior.
- If a requirement cannot be tested because the API is missing, write the test as disabled with @Disabled and a clear message, OR write it failing intentionally if the goal is to expose missing implementation.
- Prefer exposing requirement gaps clearly.
- Do not silently skip missing requirements.
- Do not use mocks anywhere.

Deliverables:
1. Add Testcontainers dependencies if missing.
2. Add test configuration if needed.
3. Create IssueFlowEndToEndIT.
4. Add any small test fixtures/resources needed.
5. Ensure the test can be run with:
   mvn test
   or
   mvn verify
6. Document in comments how to run this end-to-end test.
7. At the end, summarize:
   - what is covered
   - what could not be tested because API support is missing
   - which requirements are currently failing

## Step 4 QA review
### 1. Check against 2.1-2.5 sections

You are a senior Java Spring Boot QA/reviewer.

Review my IssueFlow system against requirements 2.1–2.5 only.

Architecture packages:
- user
- security/auth/jwt
- project
- ticket
- comment
- common/exception
- common/api

Requirements to verify:

2.1 User Management
- Register user with username, email, full_name, role.
- Role must be ADMIN or DEVELOPER.
- Fetch user by id.
- Update user full name and role.
- Delete user.
- Fetch all users.

2.2 Authentication
- All API endpoints must be protected by JWT authentication.
- POST /auth/login accepts username and password and returns signed JWT access token.
- POST /auth/logout invalidates current token using deny-list or expiry.
- GET /auth/me returns currently authenticated user profile.

2.3 Project Management
- Create project with name, description, owner userId.
- Fetch project by id.
- Update project name or description.
- Delete project.
- Fetch all projects.

2.4 Ticket Management
- Create ticket with title, description, status, priority, type, projectId, optional assigneeId.
- Fetch ticket by id.
- Update ticket fields: title, description, status, priority, assigneeId.
- Delete ticket.
- Fetch all tickets by project.
- Prevent simultaneous update of same ticket by two or more users.
- Status must be TODO, IN_PROGRESS, IN_REVIEW, DONE.
- Priority must be LOW, MEDIUM, HIGH, CRITICAL.
- Type must be BUG, FEATURE, TECHNICAL.
- Ticket cannot be updated once DONE.
- Status lifecycle only moves forward:
  TODO -> IN_PROGRESS -> IN_REVIEW -> DONE.
  Backward transitions are forbidden.

2.5 Comment Management
- Add comment to ticket with content and authorId.
- Fetch all comments for a ticket.
- Update comment content.
- Delete comment.
- Prevent two users from editing the same comment at the same time.

Your task:
Check the whole implementation together, not module by module in isolation.

Review:
1. Controllers
2. Services
3. Entities
4. DTOs
5. Repositories
6. Mappers
7. Security config
8. JWT filter/token service/logout behavior
9. Validation annotations
10. Exception handling and HTTP status codes
11. Database constraints and optimistic locking/version fields if used
12. Integration between modules:
   - project owner must reference an existing user
   - ticket projectId must reference an existing project
   - ticket assigneeId must reference an existing user if provided
   - comment ticket must exist
   - comment author must exist
   - authenticated user behavior for /auth/me
   - protected endpoints cannot be used without valid JWT

Important:
Base your review strictly on requirements 2.1–2.5.
Do not invent extra requirements.
Separate real requirement violations from optional improvements.

Check edge cases:

User:
- create user with missing username/email/full_name/role
- create user with invalid role
- fetch non-existing user
- update non-existing user
- update invalid role
- delete non-existing user
- fetch all users when empty

Authentication:
- login with valid credentials
- login with wrong username/password
- access protected endpoint without token
- access protected endpoint with invalid token
- access protected endpoint with expired/denied token
- logout token and try using same token again
- /auth/me with valid token
- /auth/me without token

Project:
- create project with missing name/description/owner
- create project with non-existing ownerId
- fetch non-existing project
- update non-existing project
- delete non-existing project
- fetch all projects when empty

Ticket:
- create ticket with missing required fields
- create ticket with invalid status/priority/type
- create ticket with non-existing projectId
- create ticket with non-existing assigneeId
- create ticket without assigneeId
- fetch non-existing ticket
- update non-existing ticket
- update DONE ticket
- move status forward legally
- move status backward illegally
- skip lifecycle steps if implementation allows/forbids it — report behavior clearly
- delete non-existing ticket
- fetch tickets by non-existing project
- simulate concurrent update by two users/transactions and verify one fails

Comment:
- add comment with missing content/authorId
- add comment to non-existing ticket
- add comment with non-existing authorId
- fetch comments for non-existing ticket
- update non-existing comment
- delete non-existing comment
- simulate concurrent comment edit by two users/transactions and verify one fails

For every problem found, output:
- Requirement section: 2.1 / 2.2 / 2.3 / 2.4 / 2.5
- File/class/method
- What is wrong
- Why it violates the requirement
- Severity: Critical / Major / Minor
- Exact fix suggestion
- Suggested test that would catch it

At the end, output:
1. Overall Pass/Fail
2. Pass/Fail per section
3. Critical must-fix issues
4. Major issues
5. Minor issues
6. Optional improvements
7. Missing integration tests
8. Recommended end-to-end test flow without mocking anything

Also check whether the implementation is internally consistent:
- field names: full_name vs fullName
- request/response DTOs match API behavior
- enum validation works before persistence
- exceptions return clean API errors
- security does not accidentally leave protected endpoints open
- delete behavior is consistent with fetch behavior
- optimistic locking/concurrency protection is actually tested, not only defined

### 2. check against 3.1-3.8 requirements
You are a senior Java Spring Boot QA/reviewer.

Review my IssueFlow system against requirements 3.1–3.8 only.

Relevant architecture packages:
- audit
- ticket
- comment
- attachment
- importexport
- scheduler
- project
- user
- common/exception
- common/api
- common/persistence
- security

Requirements to verify:

3.1 Audit Log
- System maintains persistent append-only audit records.
- All state-changing actions are recorded.
- Includes manual user actions and automatic system actions.
- Endpoint exists to retrieve all logs.
- Endpoint supports filtering by a specific field.

3.2 Ticket Dependencies
- POST /tickets/{ticketId}/dependencies with body { "blockedBy": 42 } adds dependency.
- GET /tickets/{ticketId}/dependencies lists tickets blocking this ticket.
- DELETE /tickets/{ticketId}/dependencies/{blockerId} removes dependency.
- Both tickets must exist.
- Both tickets must belong to the same project.
- A ticket cannot transition to DONE if it has unresolved blockers.

3.3 Attachment Management
- Users can attach files to tickets.
- Max file size is 10 MB.
- Allowed file types:
  - image/png
  - image/jpeg
  - application/pdf
  - text/plain
- All other file types are rejected.

3.4 Ticket Export & Import
- GET /tickets/export?projectId={id} returns CSV file.
- CSV includes:
  - id
  - title
  - description
  - status
  - priority
  - type
  - assigneeId
- POST /tickets/import accepts multipart/form-data CSV file.
- Import request specifies target projectId as form field.
- Import creates tickets in bulk.
- Import returns summary:
  { "created": 42, "failed": 3, "errors": [...] }
- CSV must correctly handle commas and quotes inside field values.

3.5 Soft Delete for Tickets and Projects
- Tickets and projects can only be soft-deleted.
- Soft-deleted records are hidden from standard API responses.
- Soft-deleted records can be recovered or audited.
- GET /tickets/deleted?projectId={id} lists only soft-deleted tickets, ADMIN only.
- GET /projects/deleted lists only soft-deleted projects, ADMIN only.
- POST /tickets/{id}/restore restores soft-deleted ticket, ADMIN only.
- POST /projects/{id}/restore restores soft-deleted project, ADMIN only.

3.6 @Mention Mechanism in Comments
- @username inside comment body creates a persisted mention association.
- Mentioned user is notified or represented by persisted notification/association behavior.
- GET /users/{userId}/mentions returns all comments where user was mentioned, newest first.
- Comment responses include:
  mentionedUsers: [{ id, username, fullName }]
- On comment update, mention list is re-evaluated.
- Newly added mentions are created.
- Removed mentions are deleted.
- Mentions match usernames case-insensitively.

3.7 Auto-Scheduling Escalation Level on Tickets
- Ticket create/update accepts optional dueDate ISO-8601 datetime.
- Overdue ticket with priority below CRITICAL is promoted one level:
  LOW -> MEDIUM -> HIGH -> CRITICAL.
- When overdue ticket reaches CRITICAL and is still overdue, is_overdue is set to true.
- is_overdue is visible in all ticket GET responses.
- Escalation is idempotent.
- CRITICAL ticket is never escalated further.
- Escalation applies only when dueDate is set.
- Manual priority change via PATCH /tickets/{id} resets auto-escalation state:
  - is_overdue cleared
  - next escalation cycle re-evaluates from new priority
- Escalation does not change ticket status.
- Only priority and is_overdue may be modified by escalation.

3.8 Auto Assignment to Users by Workload
- On ticket creation, if assigneeId is absent, system auto-selects least-loaded DEVELOPER in the project.
- Workload = count of non-DONE tickets assigned to each user in same project.
- Lowest workload user is selected.
- Ties are broken by user registration order, oldest first.
- If no DEVELOPER users are linked to the project, ticket is created with assigneeId = null.
- GET /projects/{projectId}/workload returns:
  { userId, username, openTicketCount }
  for all users in the project.
- Workload response sorted by openTicketCount ascending.
- Auto-assignment is recorded in Audit Log:
  actor = SYSTEM
  action = AUTO_ASSIGN
- Only DEVELOPER users are candidates.
- ADMIN users are excluded.
- Explicit assigneeId in PATCH /tickets/{id} overrides auto-assignment.
- Auto-assignment is not triggered on ticket update.
- Auto-assignment runs only on creation when assigneeId is absent.

Your task:
Check the whole implementation together, not module by module in isolation.

Review:
1. Controllers
2. Services
3. Entities
4. DTOs
5. Repositories
6. Mappers
7. Schedulers
8. File upload/storage validation
9. CSV import/export implementation
10. Security and ADMIN-only access
11. Audit event publishing
12. Database constraints
13. Soft-delete implementation
14. Exception handling and HTTP status codes
15. Integration between features:
   - DONE transition must check unresolved dependencies
   - auto-assignment must create audit log
   - scheduler escalation must create audit log if requirement/system design records all state-changing actions
   - soft-deleted tickets/projects must not appear in normal fetch/list/export/workload responses
   - restored records must appear again in normal responses
   - mentions must update correctly when comments change
   - attachments must belong to existing tickets
   - import must respect ticket validation rules

Important rules:
- Base your review strictly on requirements 3.1–3.8.
- Do not invent extra requirements.
- Separate real requirement violations from optional improvements.
- Check real behavior, not only whether classes exist.

Check edge cases:

Audit Log:
- create/update/delete/restore project creates audit log
- create/update/delete/restore ticket creates audit log
- add/update/delete comment creates audit log
- add/remove dependency creates audit log
- upload attachment creates audit log
- import tickets creates audit log
- auto-assignment creates audit log with actor SYSTEM and action AUTO_ASSIGN
- escalation creates audit log as SYSTEM
- audit records cannot be updated/deleted through API
- filtering works by supported field
- empty audit log returns empty list

Ticket Dependencies:
- add dependency with non-existing ticketId
- add dependency with non-existing blockedBy
- add dependency across different projects
- add duplicate dependency
- remove existing dependency
- remove non-existing dependency
- list dependencies when none exist
- ticket cannot move to DONE while blocker is unresolved
- ticket can move to DONE after blocker is DONE
- self-dependency should be rejected if implemented; if not, report risk separately as optional unless required elsewhere
- circular dependency should be reported if not handled, but mark optional unless requirement explicitly says cycles forbidden

Attachments:
- upload valid PNG/JPEG/PDF/TXT
- reject file larger than 10 MB
- reject invalid MIME type
- reject missing file
- upload to non-existing ticket
- ensure content type is validated safely, not only by filename extension
- ensure attachment metadata is persisted

Export/Import:
- export tickets for existing project
- export tickets for project with no tickets
- export non-existing projectId
- export excludes soft-deleted tickets
- CSV headers exactly match required fields
- CSV escapes commas and quotes correctly
- import valid CSV
- import CSV with commas and quotes in title/description
- import missing required columns
- import invalid enum values
- import into non-existing projectId
- import with invalid assigneeId
- import partial success returns correct created/failed/errors summary
- import does not create invalid rows

Soft Delete:
- delete ticket/project performs soft delete, not hard delete
- soft-deleted ticket/project hidden from normal GET/list
- deleted endpoints list only deleted records
- deleted endpoints require ADMIN
- restore endpoints require ADMIN
- restore non-existing record
- restore record that is not deleted
- project soft delete behavior with tickets is consistent and documented by implementation
- audit log still keeps deleted/restored actions

Mentions:
- comment with @username creates mention
- case-insensitive matching works: @John matches username john
- multiple mentions in one comment
- duplicate same username mention creates only one association
- unknown username mention does not crash
- comment response includes mentionedUsers
- GET /users/{userId}/mentions returns newest first
- update comment adds new mention
- update comment removes old mention
- delete comment handles mention associations correctly

Escalation:
- create/update ticket accepts dueDate
- dueDate ISO-8601 parsing works
- no dueDate means no escalation
- overdue LOW becomes MEDIUM
- overdue MEDIUM becomes HIGH
- overdue HIGH becomes CRITICAL
- overdue CRITICAL sets/is keeps is_overdue true
- non-overdue ticket is not escalated
- DONE ticket behavior: check whether escalation applies or not; report if unclear
- escalation does not change status
- escalation is idempotent
- manual priority PATCH clears is_overdue
- manual priority PATCH resets escalation state
- scheduler handles multiple overdue tickets
- escalation creates audit logs because it is a state-changing system action

Auto Assignment:
- create ticket without assignee assigns least-loaded DEVELOPER in same project
- ADMIN users are excluded
- only project members are considered
- tie broken by oldest registered user
- no DEVELOPER project members means assigneeId null
- explicit assigneeId on create skips auto-assignment
- update without assigneeId does not trigger auto-assignment
- PATCH assigneeId overrides current assignee
- workload counts only non-DONE tickets
- workload counts only tickets in same project
- workload endpoint includes all users in project
- workload endpoint sorted ascending
- auto-assignment creates audit log with SYSTEM/AUTO_ASSIGN

For every issue found, output:
- Requirement section: 3.1 / 3.2 / 3.3 / 3.4 / 3.5 / 3.6 / 3.7 / 3.8
- File/class/method
- What is wrong
- Why it violates the requirement
- Severity: Critical / Major / Minor
- Exact fix suggestion
- Suggested test that would catch it

At the end, output:
1. Overall Pass/Fail
2. Pass/Fail per section
3. Critical must-fix issues
4. Major issues
5. Minor issues
6. Optional improvements
7. Missing integration tests
8. Recommended end-to-end test flow without mocking anything

Also verify:
- standard API responses do not leak soft-deleted records
- enum validation happens before persistence
- CSV parser/writer is real CSV-safe, not simple split(",")
- multipart upload validation is actually enforced
- concurrent/security behavior is tested where relevant
- system actions are distinguishable from user actions in audit logs
- scheduler uses configurable time/clock where possible

