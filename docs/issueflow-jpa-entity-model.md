# IssueFlow JPA Entity Model Design Review

## Scope

This design covers persistence/domain modeling only for Java 21, Spring Boot 3, PostgreSQL, Spring Data JPA, and Hibernate. It intentionally excludes services, controllers, authorization flow, and API DTO design.

## Model Summary

`User`, `Project`, `Ticket`, `Comment`, and `Attachment` are soft deletable aggregate-facing entities. `ProjectMember`, `TicketDependency`, `Mention`, and `AuditLog` are append/link/event entities and are not soft deleted by default.

All entities inherit from `AuditableEntity`, which provides:

- `id` as a database-generated surrogate key.
- `version` for optimistic locking.
- `created_at` and `updated_at` timestamps.
- `created_by` and `updated_by` actor ids.

Soft-deletable entities inherit from `SoftDeletableAuditableEntity`, adding `deleted_at` and `deleted_by`.

## Relationship Choices

`User -> Project`

`Project.owner` is a required `@ManyToOne(fetch = LAZY)`. A project has one accountable owner, while one user can own many projects. The inverse collection is marked `@JsonIgnore` and batch-sized because it is navigational, not usually part of a user read model.

`Project -> ProjectMember -> User`

Membership is modeled as an entity instead of a direct many-to-many because it has attributes: role, joined date, audit fields, and future room for notification or permission flags. A unique constraint on `(project_id, user_id)` prevents duplicate membership.

`Project -> Ticket`

`Ticket.project` is required and lazy. The inverse `Project.tickets` does not cascade deletes because tickets are business records; deleting a project should be a deliberate domain operation, normally a soft delete or archival workflow.

`Ticket -> User`

`Ticket.reporter` is required. `Ticket.assignee` is optional to support unassigned triage. Both are lazy to avoid accidental user hydration in ticket list queries.

`Ticket -> Comment`

`Ticket.comments` uses `cascade = ALL` and `orphanRemoval = true` because comments are dependent children of a ticket in this model. Comments are also soft deletable, so normal deletes become updates.

`Ticket -> Attachment`

Attachments are metadata rows owned by a ticket, with binary content stored externally by `storage_key`. Cascading from ticket to attachment metadata is acceptable, but deleting storage objects must remain an application/storage concern.

`Comment -> Mention -> User`

Mentions are stored as durable rows derived from comment body parsing. The row references the comment, ticket, and mentioned user. The ticket reference is denormalized intentionally to support fast notification/inbox queries without joining through comments every time.

`Ticket -> TicketDependency -> Ticket`

Dependencies are directed graph edges. `ticket` is the dependent ticket; `dependsOnTicket` is the prerequisite/blocking ticket. The unique edge and self-dependency check belong in the database. Cycle detection cannot be reliably enforced with simple JPA annotations and should be handled transactionally in domain logic or with a recursive SQL check.

`AuditLog`

Audit logs store entity type/id instead of polymorphic JPA relationships. This avoids FK churn, works for deleted rows and auth events, and keeps audit append-only. The tradeoff is that the application must validate entity references when writing events.

## Enum Strategy

Enums are persisted with `EnumType.STRING`, not ordinal. This makes data readable and protects existing rows when enum declarations are reordered. The tradeoff is slightly wider indexes and the need to manage rename migrations carefully.

Current enum families:

- `Role`: global user/application role.
- `ProjectMemberRole`: per-project role.
- `TicketType`, `TicketStatus`, `TicketPriority`.
- `TicketDependencyType`.
- `AuditActorType`, `AuditAction`, `AuditableEntityType`.

## Constraints

Important database constraints:

- Users: unique active username/email through partial PostgreSQL indexes.
- Projects: unique active project key through a partial PostgreSQL index.
- Project members: unique `(project_id, user_id)`.
- Ticket dependencies: unique `(ticket_id, depends_on_ticket_id)` and `ticket_id <> depends_on_ticket_id`.
- Mentions: unique `(comment_id, mentioned_user_id)` to prevent duplicate notification rows per comment.
- Attachments: non-negative `size_bytes`.
- Enum columns: check constraints in Flyway migrations.

JPA annotations express local nullability and uniqueness where portable. PostgreSQL partial unique indexes are kept in migrations because standard JPA cannot model partial indexes.

## Index Recommendations

Indexes are aligned to expected access patterns:

- Ticket boards: `(project_id, status)`.
- User workload: `(assignee_id, status)`.
- Escalation/SLA scans: `(assignee_id, due_at)` filtered to open tickets.
- Reporter history: `(reporter_id)`.
- Comment timelines: `(ticket_id, created_at DESC)`.
- Mention inbox: `(mentioned_user_id, created_at)`.
- Dependency traversal: both `(ticket_id)` and `(depends_on_ticket_id)`.
- Audit queries: `(entity_type, entity_id, created_at)` and `(actor_type, actor_id, created_at)`.

The tradeoff is write overhead. These are appropriate because IssueFlow will read board, workload, timeline, mention, and audit views much more often than it mutates indexes at very high volume.

## Optimistic Locking

Every entity has a `@Version long version`. This protects concurrent edits to mutable records such as tickets, comments, projects, and users. Link/event rows also inherit it for consistency, although contention there should be rare.

Soft deletes include the version in `@SQLDelete`, so deleting a stale entity fails rather than silently overwriting a newer update.

## Soft Delete Strategy

Soft-deletable entities use:

- `deleted_at` and `deleted_by`.
- Hibernate `@SQLDelete` to convert deletes into updates.
- Hibernate `@SQLRestriction("deleted_at IS NULL")` to hide deleted rows from normal queries.
- Partial PostgreSQL indexes scoped to `deleted_at IS NULL`.

Tradeoff: `@SQLRestriction` is always on. That is desirable for normal product queries, but admin restore/reporting flows need dedicated repository methods, native queries, or a separate filter-based strategy if they must include deleted rows.

## Fetch Strategy

All `@ManyToOne` associations are explicitly `LAZY`. JPA defaults many-to-one to eager, which is a common source of N+1 query problems.

Collections are also lazy and annotated with `@BatchSize(size = 50)`. List/detail screens should use repository-level `@EntityGraph`, fetch joins, projections, or DTO queries to load exactly the associations they need.

Avoid serializing entities directly from controllers. Use DTOs and mapper queries. Entity collections and sensitive fields are marked with `@JsonIgnore` as a defensive layer against circular serialization and accidental password/hash exposure.

## Cascade Strategy

Recommended cascade policy:

- `Project.members`: cascade all and orphan removal, because membership rows are owned by a project.
- `Ticket.comments`, `Ticket.attachments`, `Ticket.dependencies`: cascade all and orphan removal, because they are children/link rows owned by the ticket aggregate.
- No cascade from tickets to users/projects, comments to users, attachments to users, or audit logs to any entity. These are independent aggregate roots or historical references.

This avoids accidentally deleting shared users/projects while keeping dependent rows easy to manage inside aggregate boundaries.

## Production Notes

Set Hibernate DDL to `validate` in production and use Flyway for schema evolution. The current model includes migration DDL so PostgreSQL owns constraints and partial indexes. For high-volume audit logs, consider monthly partitioning by `created_at` after query volume and retention rules are known.
