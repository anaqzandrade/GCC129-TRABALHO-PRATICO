# Offer Service

Serviço de ofertas de doação da Ravita. Ele é dono do `offer_db` e participa da SAGA de redistribuição
como passo **T2**: reservar a oferta (`ReserveOffer`) e, na compensação, liberar a reserva (`ReleaseOffer`).
O orquestrador é o redistribution-service.

- Java 21, Spring Boot 3.5.7 e Maven
- PostgreSQL 17, com o schema versionado pelo Flyway (`src/main/resources/db/migration`)
- Porta **8082**

## Endpoints (contrato `docs/arquitetura/02-contratos-rest.md`, seção 3)

| Método e caminho | Quem usa | Sucesso | Erros específicos |
|---|---|---|---|
| `POST /api/v1/offers` | doador (via Portal BFF) | `201 Offer`, `Location`, `ETag` | `422 INVALID_DONOR`, `INVALID_QUANTITY`, `INVALID_WINDOW` |
| `GET /api/v1/offers` | BFFs | `200 Page<OfferSummary>` | `400` para filtro desconhecido ou inválido |
| `GET /api/v1/offers/available` | Portal BFF e assistente (LLM) | `200 Page<AvailableOffer>`, lido do modelo de leitura (CQRS) | `400` para filtro desconhecido ou inválido |
| `GET /api/v1/offers/{offerId}` | BFFs e serviços | `200 Offer`, `ETag` | `404` |
| `GET /api/v1/offers/statistics` | Admin BFF | `200 Statistics` | — |
| `POST /api/v1/offers/{offerId}/reservations` | redistribution | `201 Reservation`, `Location` | `409 OFFER_NOT_AVAILABLE`, `422 INVALID_REDISTRIBUTION` |
| `GET /api/v1/offers/{offerId}/reservations/{reservationId}` | redistribution | `200 Reservation` | `404` |
| `DELETE /api/v1/offers/{offerId}/reservations/{reservationId}` | redistribution | `204` (também na repetição) | `409 OFFER_ALREADY_COLLECTED`, `404` |

Filtros de `GET /offers`: `page` (≥ 1, padrão 1), `size` (1 a 100, padrão 20), `status`, `category`, `city`
e `donorId`. O filtro `city` ignora acentos e maiúsculas. Com `status=AVAILABLE`, ofertas cuja janela já
terminou ficam de fora.

Filtros de `GET /offers/available`: `page`, `size`, `category` e `city`. A resposta vem do modelo de
leitura e traz `lastEvent`, `eventOccurredAt` e `projectedAt`, que mostram de qual evento veio o dado.

Todas as respostas devolvem `X-Correlation-ID`, e todo erro segue o formato `{code, message, correlationId, details?}`.

## Como rodar

**Com Docker (a partir desta pasta):**

```bash
docker compose up --build
curl http://localhost:8082/actuator/health
```

Para criar uma oferta, o organization-service precisa estar rodando, porque o doador é validado nele.
Sem ele, `POST /offers` responde `503 DEPENDENCY_UNAVAILABLE`. As consultas e as reservas funcionam mesmo assim.

**Testes (não precisam de Docker nem de PostgreSQL):**

```bash
mvn test
```

**Fluxo da SAGA na mão (Git Bash):**

```bash
# 1. criar oferta (doador precisa existir no organization-service)
curl -i -X POST http://localhost:8082/api/v1/offers -H "Content-Type: application/json" -d '{
  "donorId": "org-...", "foodCategory": "VEGETABLES", "description": "Hortaliças variadas",
  "quantity": 30, "unit": "KG",
  "availableFrom": "2026-11-17T08:00:00-03:00", "availableUntil": "2026-11-17T18:00:00-03:00"}'

# 2. T2 - reservar (repetir com o mesmo redistributionId devolve a mesma reserva)
curl -i -X POST http://localhost:8082/api/v1/offers/{offerId}/reservations \
  -H "Content-Type: application/json" -d '{"redistributionId": "red-1"}'

# 3. compensação - liberar (a oferta volta para AVAILABLE)
curl -i -X DELETE http://localhost:8082/api/v1/offers/{offerId}/reservations/{reservationId}
```

## Compose da raiz

Bloco para colar no `docker-compose.yml` da raiz do repositório. A rede `services` deve ser a mesma
usada pelos outros serviços de domínio:

```yaml
services:
  offer-db:
    image: postgres:17-alpine
    environment:
      POSTGRES_DB: offer_db
      POSTGRES_USER: ${OFFER_DB_USER:-offer}
      POSTGRES_PASSWORD: ${OFFER_DB_PASSWORD:-offer}
    volumes:
      - offer-db-data:/var/lib/postgresql/data
    networks: [offer-data]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB}"]
      interval: 5s
      timeout: 3s
      retries: 10

  offer-service:
    build: ./offer-service
    environment:
      SERVER_PORT: 8082
      DB_HOST: offer-db
      DB_PORT: 5432
      DB_NAME: offer_db
      DB_USER: ${OFFER_DB_USER:-offer}
      DB_PASSWORD: ${OFFER_DB_PASSWORD:-offer}
      ORGANIZATION_SERVICE_URL: http://organization-service:8081
    depends_on:
      offer-db: { condition: service_healthy }
    networks: [offer-data, services]
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8082/actuator/health > /dev/null || exit 1"]
      interval: 10s
      timeout: 3s
      start_period: 40s
      retries: 5

networks:
  offer-data: { internal: true }
  services:

volumes:
  offer-db-data:
```

No redistribution-service: `OFFER_SERVICE_URL=http://offer-service:8082`.
Sem `ports`, dá para escalar: `docker compose up -d --scale offer-service=3`.

## Decisões técnicas

- **Dockerfile multi-stage.** O estágio de build tem JDK e Maven; a imagem final tem só o JRE
  (alpine) e roda com um usuário sem privilégios. O `pom.xml` é copiado antes do código, então as
  dependências ficam em cache. O jar é separado em camadas (`-Djarmode=tools`): mudar só o código
  reconstrói só a última camada.
- **Concorrência na reserva.** A `Offer` tem `@Version`. Se duas redistribuições reservam a mesma
  oferta ao mesmo tempo, só uma grava; a outra recebe `409 OFFER_NOT_AVAILABLE` (doc 04, seção 15).
  A mesma versão gera o `ETag` (`"v0"`, `"v1"`…).
- **Comandos idempotentes da SAGA.** A reserva é única por `redistributionId`: repetir devolve a mesma.
  Liberar uma reserva já liberada responde `204` sem efeito. Liberar depois do fim da janela marca a
  oferta como `EXPIRED`, e não `AVAILABLE`.
- **Nenhuma chamada HTTP dentro de transação.** O doador é validado no organization-service antes de
  abrir a transação, para não segurar uma conexão do banco esperando a rede. A chamada tem timeout
  (`ORGANIZATION_SERVICE_TIMEOUT`, padrão 2s); falha vira `503`, e timeout vira `504`.
- **Schema pelo Flyway** (`ddl-auto: none`): o Hibernate nunca altera tabelas.
- **Expiração.** Um job (`OFFER_EXPIRATION_INTERVAL_MS`, padrão 60s) marca como `EXPIRED` as ofertas
  disponíveis cuja janela terminou. Cada oferta é expirada numa transação própria, com `@Version`: se
  várias réplicas rodam o job ao mesmo tempo, só uma grava a mudança e o evento.
- **Transactional Outbox** (doc 03, seções 6 a 9). Criar, reservar, liberar e expirar gravam, na mesma
  transação, uma linha em `outbox_events` (migration `V2`). O `OutboxPublisher` publica as linhas
  `PENDING` em `ravita.events` a cada 1s (`OUTBOX_INTERVAL_MS`) e só marca `PUBLISHED` depois da
  confirmação do RabbitMQ (publisher confirm). Com o RabbitMQ fora do ar, a API continua funcionando e
  os eventos esperam no outbox; por isso o RabbitMQ não entra no `/actuator/health` deste serviço.
  `FOR UPDATE SKIP LOCKED` impede que duas réplicas publiquem a mesma linha.

| Mudança | Evento | Routing key |
|---|---|---|
| criar oferta | `OfferCreated` | `offer.created` |
| reservar (T2 da SAGA) | `OfferReserved` | `offer.reserved` |
| liberar a reserva (compensação) | `OfferReleased` (ou `OfferExpired`, se a janela já acabou) | `offer.released` (ou `offer.expired`) |
| job de expiração | `OfferExpired` | `offer.expired` |

- **CQRS** (doc 03, seções 11 a 15). O modelo de escrita é a tabela `offers`, usada pelos comandos. O
  modelo de leitura é a tabela `available_offers_read` (migration `V3`), desnormalizada (inclui o nome do
  doador) e atualizada só pelos eventos: o próprio serviço consome a fila `offer-service.read-model-queue`
  (`offer.#`). Cada evento traz o retrato da oferta depois da mudança, e a projeção só aplica um evento
  mais novo que o já aplicado (`aggregateVersion`), então eventos repetidos ou fora de ordem não estragam
  a leitura. `GET /offers/available` lê só dessa tabela; na prática a defasagem fica em torno de 1 s,
  o intervalo do publicador do outbox (o limite combinado no doc 03 é 5 s). A reserva continua validada no modelo de escrita: uma oferta que ainda
  aparece como disponível na leitura não pode ser reservada duas vezes.

## Pendências (Parte 4)

- `PATCH /offers/{id}` com `If-Match`, `POST /offers/{id}/cancellation` e os links `edit`/`cancel`
- `Idempotency-Key` durável em todo `POST`
- Eventos `OfferUpdated` e `OfferCancelled`, quando existirem o `PATCH` e o cancelamento (o outbox já
  está pronto para eles)
- Consumir `logistics.food.collected` e `logistics.food.delivered` (→ `COLLECTED` / `COMPLETED`)
- Escopo por usuário (`Offer` × `OfferSummary`, quem pode reservar), quando o gateway e os BFFs
  passarem a identidade
