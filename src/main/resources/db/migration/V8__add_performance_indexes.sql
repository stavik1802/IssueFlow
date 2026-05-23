CREATE INDEX idx_tickets_project_priority_status
    ON tickets (project_id, priority, status)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_tickets_open_assignee_due
    ON tickets (assignee_id, due_at)
    WHERE deleted_at IS NULL AND status <> 'DONE';

CREATE INDEX idx_comments_recent_by_ticket
    ON comments (ticket_id, created_at DESC)
    WHERE deleted_at IS NULL;
