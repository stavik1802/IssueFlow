ALTER TABLE ticket_dependencies
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN deleted_by BIGINT;

CREATE INDEX idx_ticket_dependencies_active_ticket
    ON ticket_dependencies (ticket_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_ticket_dependencies_active_depends_on
    ON ticket_dependencies (depends_on_ticket_id)
    WHERE deleted_at IS NULL;
