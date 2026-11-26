-- Transactional Outbox (doc 03, seções 6 a 9).
-- Cada mudança de estado da oferta grava uma linha aqui NA MESMA TRANSAÇÃO que altera a tabela offers.
-- O OutboxPublisher lê as linhas PENDING, publica no RabbitMQ e marca PUBLISHED.
-- Assim a oferta e o evento são gravados juntos ou nenhum dos dois é gravado: não há dual write.

CREATE TABLE outbox_events (
    event_id       VARCHAR(36)  PRIMARY KEY,
    aggregate_type VARCHAR(30)  NOT NULL DEFAULT 'Offer',
    aggregate_id   VARCHAR(100) NOT NULL,
    event_type     VARCHAR(40)  NOT NULL,
    routing_key    VARCHAR(60)  NOT NULL,
    -- JSON exatamente como vai para o RabbitMQ
    payload        TEXT         NOT NULL,
    -- PENDING, PUBLISHED ou FAILED
    status         VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
    attempt_count  INT          NOT NULL DEFAULT 0,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at   TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

-- o publicador busca sempre as pendentes mais antigas
CREATE INDEX idx_outbox_pending ON outbox_events (status, created_at);
