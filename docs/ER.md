# Modelo relacional

Fonte: `src/main/resources/db/migration/V1__initial_schema.sql`. Diagrama resumido; a migration é a definição completa de colunas e constraints.

```mermaid
erDiagram
    assignors ||--o{ receivables : cede
    assignors ||--o{ settlements : identifica
    receivables ||--o| settlements : liquida
    exchange_rates |o--o{ settlements : usada_em
    assignors {
        uuid id PK
        varchar code UK
        varchar name
    }
    receivables {
        uuid id PK
        uuid assignor_id FK
        varchar title_code
        varchar type
        numeric face_value
        date due_date
        varchar payment_currency
        varchar status
        timestamptz created_at
        timestamptz updated_at
    }
    exchange_rates {
        uuid id PK
        varchar currency_pair
        numeric rate
        timestamptz valid_from
        timestamptz created_at
    }
    settlements {
        uuid id PK
        uuid receivable_id FK,UK
        uuid assignor_id FK
        uuid idempotency_key UK
        varchar request_fingerprint
        uuid exchange_rate_id FK
        numeric present_value_brl
        numeric discount_brl
        varchar currency
        numeric final_amount
        timestamptz settled_at
    }
    pricing_config {
        integer id PK
        numeric base_rate
        integer max_term_months
        integer exchange_validity_hours
        timestamptz updated_at
    }
```

- Cedente possui zero ou mais títulos; cada título possui no máximo uma liquidação (`UNIQUE receivable_id`). `UNIQUE(assignor_id, title_code)` impede duplicar a identificação comercial.
- Cotações são únicas por `(currency_pair, valid_from)`. Liquidação BRL não referencia câmbio; USD exige referência, valor positivo e vigência no snapshot.
- `pricing_config` é singleton (`id = 1`), sem FK de liquidação: parâmetros efetivos são copiados para o snapshot, não lidos retrospectivamente.
- `settlements` também guarda nome/código do cedente/título, tipo, face, vencimento, referência, prazo, base, spread, taxa efetiva e câmbio usado/vigência. A duplicação deliberada preserva o histórico mesmo que os cadastros mudem.
- Dinheiro NUMERIC(19,2); taxas NUMERIC(19,10); câmbio NUMERIC(24,10). CHECKs validam sinais, domínio, soma de taxas, deságio e coerência BRL/USD. Timestamps são TIMESTAMPTZ, serializados em UTC.
- Índices: recebíveis por criação/ID, liquidações por instante/ID e por cedente/instante. Filtro por moeda não tem índice dedicado; medir antes de acrescentar.

Não há tabela de simulação, lote ou importação. Estados do recebível são PENDING/SETTLED; estados de reconfirmação/erro/incerteza pertencem ao fluxo da interface. A aplicação não oferece alteração de liquidações; a demonstração não implementa proteção contra UPDATE SQL executado por administrador.
