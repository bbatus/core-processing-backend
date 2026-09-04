-- CPB'nin KENDI tabloları. `ticket`, `ticket_context`, `ai_dispatch`, `ai_action_inbox` BURADA
-- OLUŞTURULMAZ — bunlar EP'nin (genaiops-event-processor) migration'ları tarafından, paylaşılan
-- şemada (genaiops_ep) zaten oluşturulmuştur. CPB bu tabloları yalnızca okur/günceller (ai_dispatch)
-- ya da ekler (ai_action_inbox) — kendi Flyway migration'ıyla ASLA tekrar oluşturmaz/değiştirmez.
--
-- ADR-08 (genaiops-event-processor reposu): PK BIGINT + explicit SEQUENCE (allocationSize=50).
-- Aynı kural burada da uygulanır — CPB'ye özgü yeni sequence'ler, EP'ninkilerle ÇAKIŞMAZ.

CREATE SEQUENCE ai_process_id_seq     START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE ai_interaction_id_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE ai_process (
    id                BIGINT      NOT NULL DEFAULT nextval('ai_process_id_seq') PRIMARY KEY,
    dispatch_id       BIGINT      NOT NULL,              -- ai_dispatch(id), FK YOK (ayrı Flyway sahipliği)
    ticket_id         BIGINT      NOT NULL,              -- ticket(id), FK YOK
    dcase_ticket_id   UUID        NOT NULL,              -- korelasyon icin (EP loglariyla eslessin)
    version           INT         NOT NULL,
    iteration         INT         NOT NULL,
    trigger_rule      VARCHAR(10) NOT NULL,              -- R4 | R5 | R6
    status            VARCHAR(30) NOT NULL DEFAULT 'IN_PROGRESS',
                      -- IN_PROGRESS, SUCCEEDED, FAILED, SKIPPED_KILL_SWITCH
    solution_uniqueid VARCHAR(64),
    solution_text     TEXT,
    ai_status         VARCHAR(50),
    inbox_action_id   BIGINT,                            -- ai_action_inbox(id), FK YOK
    error_message     TEXT,
    started_at        TIMESTAMP   NOT NULL DEFAULT now(),
    finished_at        TIMESTAMP,
    duration_ms        BIGINT,
    CONSTRAINT uq_ai_process UNIQUE (dispatch_id, iteration)
);

CREATE TABLE ai_interaction (
    id               BIGINT       NOT NULL DEFAULT nextval('ai_interaction_id_seq') PRIMARY KEY,
    process_id       BIGINT       NOT NULL REFERENCES ai_process(id),
    endpoint         VARCHAR(100) NOT NULL,
    http_method      VARCHAR(10)  NOT NULL,
    attempt_no       INT          NOT NULL,
    request_body     JSONB        NOT NULL,
    response_status  INT,
    response_body    TEXT,
    error_message    TEXT,
    duration_ms      BIGINT,
    created_at       TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_process_dispatch ON ai_process(dispatch_id);
CREATE INDEX idx_ai_process_ticket ON ai_process(ticket_id);
CREATE INDEX idx_ai_process_status ON ai_process(status);
CREATE INDEX idx_ai_interaction_process ON ai_interaction(process_id);

ALTER SEQUENCE ai_process_id_seq     OWNED BY ai_process.id;
ALTER SEQUENCE ai_interaction_id_seq OWNED BY ai_interaction.id;
