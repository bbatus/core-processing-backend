-- Bu dosya CPB'nin migration'ı DEĞİLDİR — yalnızca entegrasyon testlerinde, EP'nin (genaiops-event-
-- processor) gerçek migration'larıyla oluşturduğu paylaşılan tabloların (ticket/ticket_context/
-- ai_dispatch/ai_action_inbox/audit_log) TESTLER İÇİN küçük bir aynasıdır — üretimde bu tabloları
-- her zaman EP oluşturur, CPB asla. Şema değişirse (EP tarafında) bu dosya da elle güncellenmeli.

CREATE SEQUENCE IF NOT EXISTS ticket_id_seq START WITH 1 INCREMENT BY 50;
CREATE TABLE IF NOT EXISTS ticket (
    id                             BIGINT      NOT NULL DEFAULT nextval('ticket_id_seq') PRIMARY KEY,
    dcase_ticket_id                UUID        NOT NULL UNIQUE,
    ticket_number                  BIGINT      NOT NULL,
    title                          VARCHAR(500) NOT NULL,
    description                    TEXT,
    current_version                INT         NOT NULL,
    status                         VARCHAR(50) NOT NULL,
    assigned_group                 VARCHAR(255) NOT NULL,
    assignee_id                    UUID,
    assignee_name                  VARCHAR(500),
    previous_human_assignee_id     UUID,
    previous_human_assignee_name   VARCHAR(500),
    customer_id                    UUID,
    customer_name                  VARCHAR(500),
    category                       VARCHAR(255),
    priority                       VARCHAR(100),
    organization_id                UUID        NOT NULL,
    lock_version                   BIGINT      NOT NULL DEFAULT 0,
    created_at                     TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at                     TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE SEQUENCE IF NOT EXISTS ticket_context_id_seq START WITH 1 INCREMENT BY 50;
CREATE TABLE IF NOT EXISTS ticket_context (
    id                  BIGINT      NOT NULL DEFAULT nextval('ticket_context_id_seq') PRIMARY KEY,
    ticket_id           BIGINT      NOT NULL REFERENCES ticket(id),
    version             INT         NOT NULL,
    context_json        JSONB       NOT NULL,
    include_attachments BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT uq_ticket_context UNIQUE (ticket_id, version)
);

CREATE SEQUENCE IF NOT EXISTS ai_dispatch_id_seq START WITH 1 INCREMENT BY 50;
CREATE TABLE IF NOT EXISTS ai_dispatch (
    id             BIGINT      NOT NULL DEFAULT nextval('ai_dispatch_id_seq') PRIMARY KEY,
    ticket_id      BIGINT      NOT NULL REFERENCES ticket(id),
    version        INT         NOT NULL,
    context_id     BIGINT      NOT NULL REFERENCES ticket_context(id),
    trigger_rule   VARCHAR(10) NOT NULL,
    trigger_event_id UUID,
    status         VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    claimed_by     VARCHAR(100),
    claimed_at     TIMESTAMP,
    completed_at   TIMESTAMP,
    cancelled_at   TIMESTAMP,
    error_message  TEXT,
    created_at     TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT uq_ai_dispatch UNIQUE (ticket_id, version)
);

CREATE SEQUENCE IF NOT EXISTS ai_action_inbox_id_seq START WITH 1 INCREMENT BY 50;
CREATE TABLE IF NOT EXISTS ai_action_inbox (
    id                    BIGINT      NOT NULL DEFAULT nextval('ai_action_inbox_id_seq') PRIMARY KEY,
    source_message_id     UUID        NOT NULL UNIQUE,
    ticket_id             BIGINT      NOT NULL REFERENCES ticket(id),
    dispatch_id           BIGINT      REFERENCES ai_dispatch(id),
    version               INT         NOT NULL,
    action_type           VARCHAR(50) NOT NULL,
    dcase_update_payload  JSONB       NOT NULL,
    is_compensation       BOOLEAN     NOT NULL DEFAULT FALSE,
    requires_approval     BOOLEAN     NOT NULL DEFAULT FALSE,
    status                VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    applied_at            TIMESTAMP,
    discarded_at          TIMESTAMP,
    error_message         TEXT,
    created_at            TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE SEQUENCE IF NOT EXISTS audit_log_id_seq START WITH 1 INCREMENT BY 50;
CREATE TABLE IF NOT EXISTS audit_log (
    id              BIGINT      NOT NULL DEFAULT nextval('audit_log_id_seq') PRIMARY KEY,
    category        VARCHAR(50) NOT NULL,
    dcase_ticket_id UUID,
    event_id        UUID,
    correlation_id  UUID,
    detail          JSONB,
    created_at      TIMESTAMP   NOT NULL DEFAULT now()
);
