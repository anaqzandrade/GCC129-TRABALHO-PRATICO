## 03. Database per Service, Consistência Eventual, Dual Write, Outbox e CQRS

---

## 1. Objetivo

Definir a estratégia de persistência da Ravita, os pontos de consistência eventual, o risco de dual write, o padrão Transactional Outbox e a aplicação de CQRS.

---

## 2. Database per Service

Cada microsserviço terá propriedade exclusiva sobre seu banco.

| Serviço | Banco |
|---|---|
| Organization Service | `organization_db` |
| Offer Service | `offer_db` |
| Demand/Matching Service | `matching_db` |
| Redistribution Service | `redistribution_db` |
| Logistics Service | `logistics_db` |

A proposta inicial utiliza **PostgreSQL em instâncias/containers separados**.

### Regra

> Um microsserviço nunca consulta ou altera diretamente o banco de outro serviço.

Quando precisar de dados externos, utilizará:

- API;
- evento;
- projeção local derivada de eventos.

---

## 3. Motivo do isolamento

O isolamento permite:

- autonomia de evolução;
- propriedade clara do dado;
- transações locais;
- menor acoplamento;
- independência de implantação.

O custo é que não existe uma única transação ACID entre todos os serviços.

Por isso, parte do sistema trabalhará com **consistência eventual**.

---

## 4. Pontos de consistência eventual

### Oferta → Matching

```text
Offer Service
    ↓
OfferCreated
    ↓
RabbitMQ
    ↓
Matching Service
```

Durante alguns instantes, o Matching pode ainda não conhecer a nova oferta.

**Defasagem aceitável proposta:** até 5 segundos.

### Offer Write Model → Offer Read Model

```text
Offer Write Model
       ↓
OfferCreated / OfferUpdated
       ↓
Offer Read Model
```

**Defasagem aceitável proposta:** até 5 segundos.

### Logística → Redistribuição

```text
Logistics Service
       ↓
FoodCollected / FoodDelivered
       ↓
Redistribution Service
```

A redistribuição pode permanecer brevemente no estado anterior até consumir o evento.

---

## 5. Dual Write

Dual write ocorre quando um serviço precisa:

1. alterar seu banco;
2. publicar um evento.

Exemplo problemático:

```text
UPDATE offers   ✓
PUBLICAR EVENTO ✕
```

O banco registra a alteração, mas outros serviços nunca recebem o evento.

Também pode ocorrer o inverso:

```text
PUBLICAR EVENTO ✓
COMMIT BANCO    ✕
```

Outros serviços acreditam que a alteração aconteceu, embora a transação local tenha falhado.

---

## 6. Transactional Outbox

Para reduzir esse risco, o Offer Service utilizará Transactional Outbox.

Na mesma transação PostgreSQL:

```sql
BEGIN;

INSERT INTO offers (...);

INSERT INTO outbox_events (
    event_id,
    event_type,
    aggregate_id,
    payload,
    created_at
);

COMMIT;
```

Assim, a oferta e o evento pendente são persistidos atomicamente.

---

## 7. Publicação da Outbox

```mermaid
flowchart LR
    API[Offer API] --> TX[Transação PostgreSQL]
    TX --> OFF[(offers)]
    TX --> OUT[(outbox_events)]
    OUT --> PUB[Outbox Publisher]
    PUB --> MQ[(RabbitMQ)]
    MQ --> MATCH[Matching Service]
    MQ --> READ[Offer Read Model]
```

Após confirmação de publicação, o registro da Outbox é marcado como processado.

---

## 8. Estrutura sugerida

```text
outbox_events
----------------------------------
event_id
aggregate_type
aggregate_id
event_type
payload
created_at
published_at
status
attempt_count
```

Estados:

```text
PENDING
PUBLISHED
FAILED
```

---

## 9. Entrega at-least-once e idempotência

A publicação e o consumo serão tratados como **at-least-once**.

Um consumidor pode receber o mesmo evento mais de uma vez.

Por isso, deverá registrar eventos processados:

```text
processed_events
----------------
event_id
processed_at
```

Antes de aplicar o efeito:

```text
evento já processado?
  sim → ignorar
  não → processar
```

---

## 10. Eventos do Offer Service

```text
OfferCreated
OfferUpdated
OfferReserved
OfferReleased
OfferCancelled
OfferExpired
```

Exemplo conceitual:

```json
{
  "eventId": "evt-123",
  "eventType": "OfferCreated",
  "occurredAt": "2026-10-20T13:00:00-03:00",
  "aggregateId": "offer-123",
  "data": {
    "donorId": "org-123",
    "foodCategory": "VEGETABLES",
    "quantity": 30,
    "unit": "KG",
    "city": "Lavras",
    "availableUntil": "2026-10-20T17:00:00-03:00"
  }
}
```

---

## 11. CQRS no Offer Service

O CQRS será aplicado ao Offer Service porque o domínio combina:

- comandos de alteração de estado;
- muitas consultas por filtros;
- pesquisa frequente de ofertas disponíveis;
- diferença clara entre modelo transacional e modelo de consulta.

---

## 12. Command Model

Comandos principais:

```text
CreateOffer
UpdateOffer
ReserveOffer
ReleaseOffer
CancelOffer
ExpireOffer
```

O Command Model utiliza `offer_db` e mantém a fonte autoritativa do estado.

---

## 13. Query Model

Consultas esperadas:

```text
Ofertas disponíveis por cidade
Ofertas por categoria
Ofertas próximas do vencimento
Ofertas de determinado doador
Ofertas compatíveis com uma região
```

Projeção conceitual:

```text
available_offers_read
--------------------------------
offer_id
donor_id
donor_name
food_category
quantity
unit
city
available_until
status
```

Os dados podem ser desnormalizados, pois o objetivo é leitura eficiente.

---

## 14. Atualização do Read Model

```mermaid
sequenceDiagram
    participant C as Cliente
    participant O as Offer Command
    participant DB as offer_db
    participant OB as Outbox
    participant MQ as RabbitMQ
    participant Q as Offer Query Model

    C->>O: CreateOffer
    O->>DB: INSERT Offer
    O->>OB: INSERT OfferCreated
    DB-->>O: Commit
    OB-->>MQ: Publicar OfferCreated
    MQ-->>Q: Consumir OfferCreated
    Q->>Q: Atualizar projeção
```

---

## 15. Defasagem aceitável

Para o projeto, o modelo de leitura poderá ficar **até 5 segundos** defasado em relação ao modelo de escrita.

Operações que exigem consistência imediata não confiam somente na projeção.

A reserva, por exemplo, sempre é validada pelo Command Model.

---

## 16. Exemplo de concorrência

Uma instituição consulta:

```text
Offer 123 → AVAILABLE
```

Outra instituição a reserva logo depois.

Mesmo que o Read Model ainda mostre `AVAILABLE`, uma nova tentativa será validada no Command Model.

Resultado esperado:

```http
409 Conflict
```

Assim:

- o Query Model oferece velocidade;
- o Command Model preserva a integridade da regra de negócio.

---

## 17. Como foi implementado

As seções anteriores registram o desenho. Esta mostra o que está no código, com os nomes reais.

### Outbox no offer-service

| Peça | Implementação |
|---|---|
| Tabela | `outbox_events` (migração `V2__create_outbox_events.sql`): `event_id`, `aggregate_id`, `event_type`, `routing_key`, `payload` (o JSON exato que vai para o RabbitMQ), `status`, `attempt_count`, `created_at`, `published_at` |
| Gravação | `OfferOutbox`, com `Propagation.MANDATORY`: só roda dentro da transação que altera `offers`. A oferta e o evento são gravados juntos ou nenhum dos dois |
| Publicação | `OutboxPublisher`, a cada 1 s (`OUTBOX_INTERVAL_MS`), lê até 50 linhas `PENDING` com `SELECT ... FOR UPDATE SKIP LOCKED`: réplicas do offer-service não publicam a mesma linha |
| Confirmação | `publisher-confirm-type: simple`: a linha só vira `PUBLISHED` depois que o RabbitMQ confirma o recebimento |
| Broker fora | Erro de conexão não conta tentativa: a linha continua `PENDING` e sai quando o RabbitMQ volta. Outros erros contam; depois de 10, a linha fica `FAILED` |

Exchange `ravita.events` (topic). Eventos e routing keys:

| Evento | Routing key | Quando |
|---|---|---|
| `OfferCreated` | `offer.created` | oferta criada |
| `OfferReserved` | `offer.reserved` | reserva confirmada na SAGA |
| `OfferReleased` | `offer.released` | compensação da SAGA: a reserva foi liberada |
| `OfferExpired` | `offer.expired` | a janela de disponibilidade terminou |

Cada mensagem leva `eventId`, `eventType`, `occurredAt`, `aggregateId` e, em `data`, o estado da oferta com
`aggregateVersion`.

### Consumidores idempotentes

| Consumidor | Fila | Idempotência |
|---|---|---|
| demand-matching (`OfferEventConsumer`) | `matching-service.offer-events-queue` | Tabela `processed_events`: o `eventId` é gravado na mesma transação que atualiza `available_offers`; um evento repetido é ignorado |
| Projeção do offer-service (`OfferProjectionConsumer`) | `offer-service.read-model-queue` (`offer.#`) | Tabela `read_model_processed_events` e a versão da oferta: evento com `aggregateVersion` menor ou igual ao já aplicado é ignorado, o que também resolve eventos fora de ordem |

Os dois consumidores tentam 3 vezes; um evento que falha nas três é descartado e registrado no log.

### CQRS no offer-service

| Lado | Onde |
|---|---|
| Comandos | `POST /api/v1/offers` e as reservas gravam em `offers` (modelo de escrita), com validação de estado e versão otimista |
| Projeção | `available_offers_read` (migração `V3__create_offer_read_model.sql`): oferta desnormalizada, com `donor_name` vindo do organization-service no `OfferCreated`, `city_normalized` para busca sem acento, `version`, `last_event_type`, `event_occurred_at` e `projected_at` |
| Consulta | `GET /api/v1/offers/available?category=&city=&page=&size=` (`AvailableOfferQueryService`): lê só a projeção, filtra por `status = 'AVAILABLE'` e ordena por `available_until` |

`event_occurred_at` e `projected_at` mostram a defasagem real entre a escrita e a leitura; nos testes ela fica
abaixo de 2 s, dentro dos 5 s definidos na seção 15. O Portal BFF usa essa consulta em `GET /portal/v1/offers`, e o
assistente de doação, na tool `buscar_ofertas_disponiveis` (doc 07).

A reserva continua validando o modelo de escrita (seção 16): uma oferta que ainda aparece na leitura, mas já foi
reservada, é recusada com `409 OFFER_NOT_AVAILABLE`.

**Como demonstrar.** `scripts/verificacao.ps1`, seção 6, para o RabbitMQ, cria uma oferta pelo Portal, mostra a
linha `PENDING` em `outbox_events` e a oferta fora da leitura; religa o RabbitMQ e mostra a linha `PUBLISHED`, a
oferta na leitura e no demand-matching.

---

## 18. Resumo

A estratégia de dados utiliza:

- PostgreSQL separado por serviço;
- consistência eventual em pontos definidos;
- Transactional Outbox no Offer Service;
- RabbitMQ;
- consumidores idempotentes;
- CQRS no Offer Service;
- defasagem de até 5 segundos no modelo de leitura.

---

## Checklist antes do commit

- [ ] Cada serviço possui banco próprio.
- [ ] Nenhum serviço acessa banco de outro.
- [ ] Pontos de consistência eventual estão identificados.
- [ ] Dual write está explicado.
- [ ] Outbox possui fluxo completo.
- [ ] CQRS possui Command e Query Model.
- [ ] Defasagem aceitável foi definida.
- [ ] Reserva sempre valida o Command Model.
