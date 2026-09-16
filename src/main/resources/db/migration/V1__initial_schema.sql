CREATE TABLE assignors (
    id UUID PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL
);

CREATE TABLE pricing_config (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    base_rate NUMERIC(19,10) NOT NULL CHECK (base_rate >= 0),
    max_term_months INTEGER NOT NULL CHECK (max_term_months BETWEEN 0 AND 120),
    exchange_validity_hours INTEGER NOT NULL CHECK (exchange_validity_hours BETWEEN 1 AND 8760),
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE exchange_rates (
    id UUID PRIMARY KEY,
    currency_pair VARCHAR(7) NOT NULL CHECK (currency_pair = 'USD/BRL'),
    rate NUMERIC(24,10) NOT NULL CHECK (rate > 0),
    valid_from TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (currency_pair, valid_from)
);

CREATE TABLE receivables (
    id UUID PRIMARY KEY,
    assignor_id UUID NOT NULL REFERENCES assignors(id),
    title_code VARCHAR(100) NOT NULL,
    type VARCHAR(30) NOT NULL CHECK (type IN ('DUPLICATA_MERCANTIL', 'CHEQUE_PRE_DATADO')),
    face_value NUMERIC(19,2) NOT NULL CHECK (face_value > 0),
    due_date DATE NOT NULL,
    payment_currency VARCHAR(3) NOT NULL CHECK (payment_currency IN ('BRL', 'USD')),
    status VARCHAR(10) NOT NULL CHECK (status IN ('PENDING', 'SETTLED')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (assignor_id, title_code)
);

CREATE TABLE settlements (
    id UUID PRIMARY KEY,
    receivable_id UUID NOT NULL UNIQUE REFERENCES receivables(id),
    idempotency_key UUID NOT NULL UNIQUE,
    request_fingerprint VARCHAR(64) NOT NULL,
    assignor_id UUID NOT NULL REFERENCES assignors(id),
    assignor_name VARCHAR(200) NOT NULL,
    title_code VARCHAR(100) NOT NULL,
    type VARCHAR(30) NOT NULL CHECK (type IN ('DUPLICATA_MERCANTIL', 'CHEQUE_PRE_DATADO')),
    face_value NUMERIC(19,2) NOT NULL CHECK (face_value > 0),
    due_date DATE NOT NULL,
    reference_date DATE NOT NULL,
    term_months INTEGER NOT NULL CHECK (term_months BETWEEN 0 AND 120),
    base_rate NUMERIC(19,10) NOT NULL CHECK (base_rate >= 0),
    spread NUMERIC(19,10) NOT NULL CHECK (spread >= 0),
    effective_rate NUMERIC(19,10) NOT NULL CHECK (effective_rate = base_rate + spread),
    present_value_brl NUMERIC(19,2) NOT NULL CHECK (present_value_brl >= 0),
    discount_brl NUMERIC(19,2) NOT NULL CHECK (discount_brl = face_value - present_value_brl),
    currency VARCHAR(3) NOT NULL CHECK (currency IN ('BRL', 'USD')),
    final_amount NUMERIC(19,2) NOT NULL CHECK (final_amount >= 0),
    exchange_rate_id UUID REFERENCES exchange_rates(id),
    exchange_rate_used NUMERIC(24,10),
    exchange_rate_valid_from TIMESTAMPTZ,
    settled_at TIMESTAMPTZ NOT NULL,
    CHECK (due_date >= reference_date),
    CHECK ((currency = 'BRL' AND exchange_rate_id IS NULL AND exchange_rate_used IS NULL
            AND exchange_rate_valid_from IS NULL AND final_amount = present_value_brl)
        OR (currency = 'USD' AND exchange_rate_id IS NOT NULL AND exchange_rate_used IS NOT NULL
            AND exchange_rate_used > 0 AND exchange_rate_valid_from IS NOT NULL))
);

CREATE INDEX receivables_listing ON receivables (created_at DESC, id DESC);
CREATE INDEX settlements_period ON settlements (settled_at DESC, id DESC);
CREATE INDEX settlements_assignor_period ON settlements (assignor_id, settled_at DESC);

INSERT INTO pricing_config VALUES (1, 0.01, 120, 24, CURRENT_TIMESTAMP);
INSERT INTO assignors VALUES ('aebc49b1-1c4a-4f86-bf4e-f0224a9aa007', 'CED-001', 'Empresa Demonstração A');
INSERT INTO assignors VALUES ('aebc49b1-1c4a-4f86-bf4e-f0224a9aa008', 'CED-002', 'Empresa Demonstração B');
-- No exchange-rate seed: a fixed date would eventually expire and mislead local demos.
