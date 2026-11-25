-- CQRS (doc 03, seções 11 a 15): modelo de leitura das ofertas.
-- Os comandos continuam gravando em offers (modelo de escrita). Esta tabela é uma projeção
-- desnormalizada, atualizada pelos eventos que o próprio offer-service publica pelo outbox,
-- e atende a consulta GET /api/v1/offers/available. Defasagem aceita: até 5 segundos.

CREATE TABLE available_offers_read (
    offer_id          VARCHAR(100)   PRIMARY KEY,
    donor_id          VARCHAR(100)   NOT NULL,
    -- desnormalizado: vem do organization-service no OfferCreated, sem consulta na leitura
    donor_name        VARCHAR(200),
    food_category     VARCHAR(30)    NOT NULL,
    description       VARCHAR(1000),
    quantity          NUMERIC(12, 3) NOT NULL,
    unit              VARCHAR(10)    NOT NULL,
    city              VARCHAR(100),
    city_normalized   VARCHAR(100),
    available_until   TIMESTAMP WITH TIME ZONE NOT NULL,
    status            VARCHAR(20)    NOT NULL,
    -- versão da oferta no último evento aplicado: evento com versão menor ou igual é ignorado
    version           BIGINT         NOT NULL,
    last_event_type   VARCHAR(40)    NOT NULL,
    -- quando a mudança aconteceu no modelo de escrita e quando chegou aqui (mostra a defasagem)
    event_occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    projected_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_offers_read_search ON available_offers_read (status, food_category, city_normalized);

-- eventos já aplicados à projeção (entrega pelo menos uma vez: o mesmo eventId pode chegar de novo)
CREATE TABLE read_model_processed_events (
    event_id     VARCHAR(64) PRIMARY KEY,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
