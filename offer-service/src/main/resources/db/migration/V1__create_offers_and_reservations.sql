-- Offer Service: modelo de escrita (Command Model) no offer_db.
-- Datas em TIMESTAMP WITH TIME ZONE (timestamptz): o PostgreSQL guarda tudo em UTC.

CREATE TABLE offers (
    id                     VARCHAR(100)   PRIMARY KEY,
    donor_id               VARCHAR(100)   NOT NULL,
    food_category          VARCHAR(30)    NOT NULL,
    description            VARCHAR(1000)  NOT NULL,
    quantity               NUMERIC(12, 3) NOT NULL,
    unit                   VARCHAR(10)    NOT NULL,
    available_from         TIMESTAMP WITH TIME ZONE NOT NULL,
    available_until        TIMESTAMP WITH TIME ZONE NOT NULL,
    pickup_street          VARCHAR(150)   NOT NULL,
    pickup_number          VARCHAR(20)    NOT NULL,
    pickup_complement      VARCHAR(100),
    pickup_district        VARCHAR(100)   NOT NULL,
    pickup_city            VARCHAR(100)   NOT NULL,
    -- cidade sem acento e em minúsculas, usada no filtro ?city=
    pickup_city_normalized VARCHAR(100)   NOT NULL,
    pickup_state           VARCHAR(2)     NOT NULL,
    pickup_postal_code     VARCHAR(8)     NOT NULL,
    status                 VARCHAR(20)    NOT NULL,
    -- controle otimista de concorrência; também vira o ETag "v<versão>"
    version                BIGINT         NOT NULL,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_offers_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_offers_window CHECK (available_from < available_until)
);

CREATE INDEX idx_offers_status_city ON offers (status, pickup_city_normalized);
CREATE INDEX idx_offers_donor ON offers (donor_id);
CREATE INDEX idx_offers_created ON offers (created_at DESC, id);

CREATE TABLE reservations (
    id                VARCHAR(100) PRIMARY KEY,
    offer_id          VARCHAR(100) NOT NULL REFERENCES offers (id),
    -- uma redistribuição tem no máximo uma reserva: repetir o pedido devolve a mesma
    redistribution_id VARCHAR(100) NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    released_at       TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_reservations_redistribution UNIQUE (redistribution_id)
);

CREATE INDEX idx_reservations_offer ON reservations (offer_id);
