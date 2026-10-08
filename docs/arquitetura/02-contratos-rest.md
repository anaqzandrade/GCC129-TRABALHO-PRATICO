# Ravita — Contratos REST da Parte 2

Complemento de [02. REST, Gateway e BFFs](./02-rest-gateway-bff.md). Este é o catálogo da proposta v1, para revisão do grupo. Define contratos, não funcionalidades já implementadas. OpenAPI é recomendado pelo enunciado; este catálogo utiliza tabelas e exemplos Markdown como formato de entrega.

## 1. Convenções compartilhadas

Todos os caminhos de domínio abaixo são relativos a `/api/v1`; não são publicados diretamente pelo Gateway. Os clientes usam `/portal/v1` e `/admin/v1`. O transporte externo usa HTTPS e autenticação `Authorization: Bearer <token>`. Chamadas internas autenticam também o serviço chamador e preservam o contexto autorizado do usuário quando aplicável.

- Corpo JSON: `Content-Type: application/json`. `GET` não recebe corpo.
- Campos não marcados como opcionais são obrigatórios; campos desconhecidos na entrada são rejeitados com `400`. Campos somente de leitura não podem ser escritos.
- `Id`: string opaca não vazia, até 100 caracteres. O servidor gera identificadores de recursos. Exemplos curtos não definem o algoritmo de geração.
- `DateTime`: string de data/hora com fuso (`2026-10-20T13:00:00-03:00`); persistência e comparações normalizam para UTC.
- Salvo indicação contrária, campos `id`, `*Id` usam Id; `createdAt`, `updatedAt` e os demais instantes usam DateTime. Campos opcionais ausentes são omitidos, salvo os explicitamente definidos como anuláveis.
- `Quantity`: número decimal positivo, no máximo três casas decimais e valor máximo `999999999.999`. Persistir como decimal exato; `UNIT` exige valor inteiro. Estes limites são proposta de contrato, a alinhar com o Integrante 3.
- `Unit`: `KG`, `L` ou `UNIT`. `FoodCategory`: `VEGETABLES`, `FRUITS`, `GRAINS`, `BAKERY`, `DAIRY`, `PREPARED_FOOD` ou `OTHER`. Os valores são proposta inicial do domínio, não imposição do enunciado.
- `Address`: `street` (string, 1–150), `number` (string, 1–20), `district` (string, 1–100), `city` (string, 1–100), `state` (duas letras maiúsculas), `postalCode` (oito dígitos); `complement` opcional (até 100). Nenhum endereço completo é publicado em busca genérica.
- Texto é validado após remover espaços nas extremidades. Exemplos com documento zerado são ilustrativos; validação de cadastro não comprova regularidade sanitária.

### Cabeçalhos e repetição

`X-Correlation-ID` é uma string de 1–128 caracteres alfanuméricos, ponto, hífen ou sublinhado; o Gateway gera um valor se ausente ou inválido e todos os componentes o propagam e retornam. Não registrar tokens, documentos ou endereços completos em logs de acesso.

Todo `POST` exige `Idempotency-Key`, string de 1–128 caracteres com o mesmo alfabeto acima. Ausência ou formato inválido retorna `400`. Mesma chave, consumidor, método, caminho e conteúdo reproduzem o resultado original; conteúdo diferente retorna `409 IDEMPOTENCY_KEY_REUSED`. Corpo e cabeçalhos de precondição participam da comparação. Chamadas simultâneas ainda em processamento podem retornar `409 REQUEST_IN_PROGRESS`. A deduplicação é durável e definida na seção 15 do documento principal.

`GET` individual e `PATCH` de Organization/Offer retornam um `ETag` forte para o recurso editável, por exemplo `"v3"`. `PATCH` e cancelamento direto de Offer exigem `If-Match`. Ausência retorna `428 PRECONDITION_REQUIRED`; versão desatualizada retorna `412 VERSION_MISMATCH`. `PATCH` recebe um objeto de atualização parcial: pelo menos um campo editável, sem `null`; objetos aninhados, se presentes, substituem o objeto completo. A operação retorna `200` com a representação atualizada e o novo `ETag`.

O BFF só preserva `ETag` quando devolve a mesma representação do recurso. Uma representação adaptada do Portal retorna `editVersion` com o token de versão de escrita do serviço (por exemplo `"v3"` como valor string), que o cliente envia em `If-Match`; não atribuir a um JSON transformado o `ETag` de outro JSON. O serviço resolve a precondição atomicamente. Repetições idempotentes autorizadas são reconhecidas antes de reavaliar a precondição original.

`201` inclui `Location` para o recurso criado. `202` inclui `Location` para acompanhar o recurso/processo. BFFs reescrevem esse cabeçalho para uma rota pública realmente existente. `204` não contém corpo. Nenhum comando é repetido automaticamente com uma chave nova.

### Listagens

`page`: inteiro >= 1, padrão 1. `size`: inteiro de 1 a 100, padrão 20. Ordenação fixa `createdAt DESC, id ASC`; filtros combinam por AND. Valores inválidos ou filtros desconhecidos retornam `400`.

`Page<T>` contém `items: T[]`, `page: integer`, `size: integer`, `total: integer >= 0`. Uma página vazia retorna `200`, inclusive além do fim. O total considera todos os resultados do filtro e do escopo autorizado. Dados podem mudar entre páginas; esta paginação não oferece snapshot transacional entre requisições.

### Erros comuns a todas as operações

Todas as respostas de erro usam `Error = {code: string, message: string, correlationId: string}`; `details` opcional é uma lista de `{field: string, reason: string}` sem ecoar dados sensíveis.

| Status | Situação e código representativo |
|---|---|
| `400` | Estrutura, filtro, campo ou cabeçalho inválido: `INVALID_REQUEST` |
| `401` | Credencial ausente/inválida: `UNAUTHENTICATED`; incluir `WWW-Authenticate: Bearer` |
| `403` | Perfil ou identidade de serviço sem autorização para a operação: `FORBIDDEN` |
| `404` | Recurso inexistente ou privado fora do escopo: `RESOURCE_NOT_FOUND` |
| `409` | Estado incompatível: `STATE_CONFLICT`; demais códigos específicos nas tabelas |
| `412` | Precondição desatualizada: `VERSION_MISMATCH` |
| `422` | Corpo estruturalmente válido viola regra de domínio: `BUSINESS_RULE_VIOLATION` |
| `428` | `If-Match` obrigatório ausente: `PRECONDITION_REQUIRED` |
| `429` | Limite de requisições: `RATE_LIMITED`, com `Retry-After` em segundos |
| `500` | Falha inesperada: `INTERNAL_ERROR` |
| `503` | Dependência indisponível: `DEPENDENCY_UNAVAILABLE` |
| `504` | Timeout de dependência: `DEPENDENCY_TIMEOUT`; comando pode ter sido executado |

As tabelas abaixo mostram sucessos e erros de negócio específicos; os erros comuns se aplicam conforme a operação. Um `202` já devolvido não muda retroativamente para erro HTTP: a falha posterior aparece no estado consultado.

```json
{
  "code": "OFFER_NOT_AVAILABLE",
  "message": "A oferta não está disponível para reserva.",
  "correlationId": "corr-999"
}
```

## 2. Organization Service

`OrganizationInput`: `name` (string, 1–150), `type` (`DONOR` ou `INSTITUTION`), `document` (string de 14 dígitos), `address: Address`; `receivingCapacity` obrigatório para `INSTITUTION`, proibido para `DONOR`: lista não vazia de `{foodCategory: FoodCategory, maxQuantity: Quantity, unit: Unit}` sem repetir categoria/unidade. A capacidade representa o máximo por lote, não um controle de estoque agregado da instituição.

`Organization`: todos os campos de entrada + `id: Id`, `active: boolean`, `createdAt: DateTime`, `updatedAt: DateTime`. `active` começa em `true`. Nome/documento não são credenciais. Cadastro administrativo não cria automaticamente usuário ou token.

`OrganizationPatch`: subconjunto de `name`, `address`, `receivingCapacity`, `active`. `type`, `document` e `id` são imutáveis nesta versão.

| Método e caminho | Entrada/acesso | Sucesso | Erros específicos |
|---|---|---|---|
| `POST /organizations` | `OrganizationInput`; administrador | `201 Organization`, Location | `409 DOCUMENT_ALREADY_REGISTERED`; `422 INVALID_ORGANIZATION` |
| `GET /organizations` | page, size, `type`, `active`; administrador | `200 Page<Organization>` | `400 INVALID_REQUEST` |
| `GET /organizations/{organizationId}` | administrador, própria organização ou serviço autorizado para validação | `200 Organization`, ETag | `404 RESOURCE_NOT_FOUND` |
| `PATCH /organizations/{organizationId}` | `OrganizationPatch`; administrador; If-Match | `200 Organization`, ETag | `412`, `428`; `422 INVALID_ORGANIZATION` |

Exemplo de criação (cabeçalhos comuns omitidos):

```json
{
  "name": "Mercado Central",
  "type": "DONOR",
  "document": "00000000000000",
  "address": {
    "street": "Rua Central", "number": "100", "district": "Centro",
    "city": "Lavras", "state": "MG", "postalCode": "37200000"
  }
}
```

Resposta `201`, `Location: /api/v1/organizations/org-123`:

```json
{
  "id": "org-123", "name": "Mercado Central", "type": "DONOR",
  "document": "00000000000000", "active": true,
  "address": {
    "street": "Rua Central", "number": "100", "district": "Centro",
    "city": "Lavras", "state": "MG", "postalCode": "37200000"
  },
  "createdAt": "2026-10-19T10:00:00-03:00",
  "updatedAt": "2026-10-19T10:00:00-03:00"
}
```

## 3. Offer Service

`OfferInput`: `donorId: Id`, `foodCategory: FoodCategory`, `description` (string, 1–1000), `quantity: Quantity`, `unit: Unit`, `availableFrom: DateTime`, `availableUntil: DateTime`; `pickupAddress: Address` opcional, assumindo cópia do endereço atual do doador quando omitido. O doador precisa estar ativo e ser do tipo `DONOR`. A cópia passa a pertencer à oferta.

`Offer`: campos de entrada (incluindo o endereço resolvido) + `id: Id`, `status: OfferStatus`, `createdAt: DateTime`, `updatedAt: DateTime`, `_links: Links`. `OfferStatus`: `AVAILABLE`, `RESERVED`, `COLLECTED`, `COMPLETED`, `CANCELLED`, `EXPIRED`.

`OfferSummary`: `id`, `donorId`, `foodCategory`, `description`, `quantity`, `unit`, `availableFrom`, `availableUntil`, `status`, `city`, `state`, `createdAt`, `_links`; não inclui endereço completo. GET individual de terceiro autorizado a descobrir ofertas retorna esse resumo; dono e serviços autorizados recebem `Offer`. Atualizações sempre retornam `Offer` ao dono.

`OfferPatch`: subconjunto de `foodCategory`, `description`, `quantity`, `unit`, `availableFrom`, `availableUntil`, `pickupAddress`. Validar o resultado completo do PATCH. Oferta precisa estar disponível e não vencida.

`Reservation`: `id: Id`, `offerId: Id`, `redistributionId: Id`, `status` (`ACTIVE` ou `RELEASED`), `createdAt: DateTime`; `releasedAt: DateTime` opcional, presente quando liberada. Cada reserva envolve todo o lote.

| Método e caminho | Entrada/acesso | Sucesso | Erros específicos |
|---|---|---|---|
| `POST /offers` | OfferInput; doador dono | `201 Offer`, Location | `422 INVALID_DONOR`, `INVALID_QUANTITY` ou `INVALID_WINDOW` |
| `GET /offers` | page, size, status, category, city, donorId; perfis autenticados; lista privada por escopo | `200 Page<OfferSummary>` | `400 INVALID_REQUEST` |
| `GET /offers/{offerId}` | dono, instituição para descoberta ou serviço autorizado | `200 Offer` ou `OfferSummary`; ETag apenas da representação fornecida | `404 RESOURCE_NOT_FOUND` |
| `PATCH /offers/{offerId}` | OfferPatch; dono; If-Match | `200 Offer`, ETag | `409 OFFER_NOT_EDITABLE`; `412`, `428`; `422` |
| `POST /offers/{offerId}/cancellation` | `{reason: string (1–500)}`; dono; If-Match | `200 Offer` cancelada | `409 OFFER_NOT_CANCELLABLE`; `412`, `428` |
| `POST /offers/{offerId}/reservations` | `{redistributionId: Id}`; somente Redistribution | `201 Reservation`, Location da reserva | `409 OFFER_NOT_AVAILABLE`; `422 INVALID_REDISTRIBUTION` |
| `GET /offers/{offerId}/reservations/{reservationId}` | somente Redistribution | `200 Reservation` | `404 RESOURCE_NOT_FOUND` |
| `DELETE /offers/{offerId}/reservations/{reservationId}` | sem corpo; somente Redistribution, dono da reserva | `204`, inclusive repetição de liberação concluída | `409 OFFER_ALREADY_COLLECTED` ou `COLLECTION_NOT_CANCELLED`; `404` |

Busca de instituição retorna ofertas `AVAILABLE` não vencidas; outros estados só podem ser consultados pelo dono, administrador ou serviço autorizado. `category` filtra `foodCategory`; `city` compara o município normalizado do endereço da oferta. Os demais filtros seguem correspondência exata.

Exemplo de `OfferPatch`: `{"quantity": 25}`. Exemplo de resposta de reserva:

```json
{
  "id": "res-123", "offerId": "offer-123", "redistributionId": "red-456",
  "status": "ACTIVE", "createdAt": "2026-10-20T12:00:00-03:00"
}
```

### Links e estados

`Links` é um objeto de relações; cada relação contém `href: string` e `method` (`GET`, `POST` ou `PATCH`). Links não executam comandos e não substituem seus corpos/precondições.

| Relação | Condição | Destino |
|---|---|---|
| `self` | Recurso visível | GET da oferta |
| `donor` | Consumidor autorizado a consultar Organization | GET da organização interna; omitido do Portal |
| `reserve` | `AVAILABLE`, não vencida, consumidor Redistribution | POST da reserva interna |
| `edit`, `cancel` | `AVAILABLE`, não vencida, dono | PATCH da oferta; POST de cancellation |
| `start-redistribution` | Portal, instituição, `AVAILABLE`, não vencida | POST `/portal/v1/redistributions`, com offerId no corpo |

Exemplo público `RESERVED`, visível ao doador: ações de reserva, edição e cancelamento direto foram removidas.

```json
{
  "id": "offer-123", "donorId": "org-123", "foodCategory": "VEGETABLES",
  "description": "Hortaliças variadas", "quantity": 30, "unit": "KG",
  "availableFrom": "2026-10-20T13:00:00-03:00",
  "availableUntil": "2026-10-20T17:00:00-03:00",
  "status": "RESERVED", "city": "Lavras", "state": "MG",
  "createdAt": "2026-10-19T10:00:00-03:00",
  "_links": {"self": {"href": "/portal/v1/offers/offer-123", "method": "GET"}}
}
```

## 4. Demand/Matching Service

`DemandInput`: `institutionId: Id`, `foodCategory: FoodCategory`, `desiredQuantity: Quantity`, `unit: Unit`, `city` (string, 1–100). Instituição deve estar ativa. `Demand` acrescenta `id`, `createdAt` e `updatedAt`. Não há PATCH ou cancelamento de demanda no escopo inicial; demanda expressa interesse para descoberta e não reserva capacidade ou alimento.

`Matches`: `demandId: Id`, `matches: Match[]`, `generatedAt: DateTime`. `Match` contém `offerId: Id` e `compatibility: HIGH`. O critério inicial é binário: mesma categoria, unidade e cidade, oferta disponível/não vencida, quantidade pelo menos igual à desejada e lote inteiro dentro da capacidade por lote da instituição. Resultados incompatíveis não entram na lista; não inventar pontuação ou níveis intermediários. A validação definitiva ocorre no comando distribuído.

| Método e caminho | Entrada/acesso | Sucesso | Erros específicos |
|---|---|---|---|
| `POST /demands` | DemandInput; instituição dona | `201 Demand`, Location | `422 INVALID_INSTITUTION` ou `INVALID_QUANTITY` |
| `GET /demands` | page, size, institutionId; própria instituição ou serviço autorizado | `200 Page<Demand>` | `400 INVALID_REQUEST` |
| `GET /demands/{demandId}` | dona ou serviço autorizado | `200 Demand` | `404 RESOURCE_NOT_FOUND` |
| `GET /demands/{demandId}/matches` | dona ou serviço autorizado; sem corpo | `200 Matches`, inclusive lista vazia | `404`; `503` se consulta essencial indisponível |

Resposta de criação para o exemplo do documento principal:

```json
{
  "id": "dem-10", "institutionId": "org-789", "foodCategory": "VEGETABLES",
  "desiredQuantity": 20, "unit": "KG", "city": "Lavras",
  "createdAt": "2026-10-20T11:00:00-03:00", "updatedAt": "2026-10-20T11:00:00-03:00"
}
```

## 5. Redistribution Service

`RedistributionInput`: `offerId: Id`, `institutionId: Id`. O solicitante deve pertencer à instituição. Aceitar o comando cria um processo durável, não uma reserva garantida.

`Redistribution`: `id`, `offerId`, `institutionId`, `status: RedistributionStatus`, `createdAt`, `updatedAt`, `correlationId`; `donorId`, `reservationId`, `logisticsId` opcionais enquanto as etapas correspondentes não estiverem concluídas; `sagaState` (código interno de etapa), `lastEvent` (nome do último evento) e `failure: {code, message}` opcionais. `cancellationStatus` é `null` quando não solicitada, ou `PENDING`, `COMPLETED`, `REJECTED`; `cancellationReason` opcional.

`RedistributionStatus`: `CREATED`, `OFFER_RESERVED`, `LOGISTICS_PENDING`, `IN_TRANSIT`, `DELIVERED`, `COMPLETED`, `FAILED`, `CANCELLED`, conforme documento 01. `sagaState` é diagnóstico, não enum de negócio usado pelo Portal. Falha terminal só deve ser apresentada como resolvida quando as compensações exigidas estiverem concluídas; pendências continuam visíveis ao Admin.

| Método e caminho | Entrada/acesso | Sucesso | Erros específicos |
|---|---|---|---|
| `POST /redistributions` | RedistributionInput; instituição dona | `202 {id, status: CREATED}`, Location | `422 INVALID_INSTITUTION`; falhas posteriores aparecem na consulta |
| `GET /redistributions` | page, size, status, donorId, institutionId; participante, administrador ou serviço | `200 Page<Redistribution>` | `400` |
| `GET /redistributions/{redistributionId}` | participante, administrador ou serviço autorizado | `200 Redistribution` | `404` |
| `POST /redistributions/{redistributionId}/cancellation` | `{reason: string (1–500)}`; participante | `202 {id, status, cancellationStatus: PENDING}`, Location da redistribuição | `409 CANCELLATION_NOT_ALLOWED` |

Duplicata com a mesma chave devolve a aceitação original. Novo pedido para processo já cancelado retorna `200` com `cancellationStatus: COMPLETED`; se já pendente, retorna `202` sem duplicar compensações. Depois de coletado ou concluído, uma nova solicitação retorna `409`.

Exemplo de consulta após conflito de reserva:

```json
{
  "id": "red-457", "offerId": "offer-123", "institutionId": "org-789",
  "status": "FAILED", "cancellationStatus": null,
  "failure": {"code": "OFFER_NOT_AVAILABLE", "message": "A oferta foi reservada por outra redistribuição."},
  "correlationId": "corr-1000",
  "createdAt": "2026-10-20T12:00:00-03:00", "updatedAt": "2026-10-20T12:00:01-03:00"
}
```

## 6. Logistics Service

`LogisticsInput`: `redistributionId: Id`, `pickupAddress: Address`, `deliveryAddress: Address`, `collectionWindow: {from: DateTime, until: DateTime}`. Os endereços são snapshots autorizados obtidos pela SAGA; janela deve caber na disponibilidade da oferta e `from < until`. Criação somente pelo Redistribution. Nesta versão há no máximo uma operação logística por redistribuição.

`Logistics`: campos de entrada + `id`, `status: LogisticsStatus`, `createdAt`, `updatedAt`; `operatorId`, `scheduledAt`, `collectedAt`, `deliveredAt`, `estimatedArrival` opcionais; horários têm tipo DateTime. `failure: {code, message}` e `cancellationReason` opcionais. `LogisticsStatus`: `PENDING`, `SCHEDULED`, `COLLECTED`, `IN_TRANSIT`, `DELIVERED`, `FAILED`, `CANCELLED`.

| Método e caminho | Entrada/acesso | Sucesso | Erros específicos |
|---|---|---|---|
| `POST /logistics` | LogisticsInput; Redistribution | `201 Logistics`, Location | `409 LOGISTICS_ALREADY_EXISTS`; `422 INVALID_WINDOW` |
| `GET /logistics` | page, size, status, redistributionId; administrador ou serviço autorizado | `200 Page<Logistics>` | `400` |
| `GET /logistics/{logisticsId}` | administrador, operador atribuído ou serviço autorizado com contexto do participante | `200 Logistics` | `404` |
| `POST /logistics/{logisticsId}/scheduling` | `{operatorId: Id, scheduledAt: DateTime}`; administrador operacional | `200 Logistics`, SCHEDULED | `409 STATE_CONFLICT`; `422 INVALID_SCHEDULE` |
| `POST /logistics/{logisticsId}/collection` | `{occurredAt: DateTime}`; operador atribuído | `200 Logistics`, COLLECTED | `409 COLLECTION_NOT_ALLOWED`; `422 INVALID_OCCURRENCE_TIME` |
| `POST /logistics/{logisticsId}/departure` | `{occurredAt: DateTime}`; operador atribuído | `200 Logistics`, IN_TRANSIT | `409 STATE_CONFLICT`; `422 INVALID_OCCURRENCE_TIME` |
| `POST /logistics/{logisticsId}/delivery` | `{occurredAt: DateTime}`; operador atribuído | `200 Logistics`, DELIVERED | `409 DELIVERY_NOT_ALLOWED`; `422 INVALID_OCCURRENCE_TIME` |
| `POST /logistics/{logisticsId}/failure` | `{code: string (1–100), message: string (1–500), occurredAt: DateTime}`; operador atribuído ou serviço operacional autorizado | `200 Logistics`, FAILED | `409 STATE_CONFLICT`; `422 INVALID_OCCURRENCE_TIME` |
| `POST /logistics/{logisticsId}/cancellation` | `{reason: string (1–500)}`; Redistribution | `200 Logistics`, CANCELLED | `409 COLLECTION_ALREADY_STARTED` |

Scheduling é aceito em `PENDING`; coleta em `SCHEDULED`; departure em `COLLECTED`; entrega em `IN_TRANSIT`. Failure é aceito em `PENDING`, `SCHEDULED`, `COLLECTED` e `IN_TRANSIT`. Cancelamento é aceito em `PENDING`, `SCHEDULED` ou `FAILED` sem coleta registrada. `collectedAt` permanece registrado mesmo em `FAILED`, impedindo compensação incorreta. Uma nova chave não reabre operação terminal. Repetição reconhecida não republica eventos.

`scheduledAt` e a ocorrência da coleta devem estar em `[from, until)`. Ocorrências não podem ser futuras nem anteriores ao último marco físico; a entrega pode ocorrer após o fim da janela de coleta. O horário declarado não permite contornar cancelamento já confirmado. Esses comandos não alteram diretamente o banco de outros serviços.

Scheduling, departure e failure completam as transições já previstas no documento 01; são detalhamentos propostos para revisão do responsável por Logistics/SAGA, não novos requisitos do professor.

Exemplo de resposta à coleta:

```json
{
  "id": "log-321", "redistributionId": "red-456", "status": "COLLECTED",
  "pickupAddress": {"street": "Rua Central", "number": "100", "district": "Centro", "city": "Lavras", "state": "MG", "postalCode": "37200000"},
  "deliveryAddress": {"street": "Rua das Flores", "number": "50", "district": "Centro", "city": "Lavras", "state": "MG", "postalCode": "37200000"},
  "collectionWindow": {"from": "2026-10-20T13:00:00-03:00", "until": "2026-10-20T17:00:00-03:00"},
  "operatorId": "operator-1", "scheduledAt": "2026-10-20T14:00:00-03:00",
  "collectedAt": "2026-10-20T14:00:00-03:00",
  "createdAt": "2026-10-20T12:01:00-03:00", "updatedAt": "2026-10-20T14:00:00-03:00"
}
```

## 7. Consultas agregadas internas

Para evitar que o dashboard percorra todas as páginas, Organization, Offer e Redistribution oferecem `GET /api/v1/{recurso}/statistics`, autorizado somente ao Admin BFF autenticado. `recurso` é `organizations`, `offers` ou `redistributions`; não há parâmetros ou corpo nesta versão. Retorna `200 Statistics`, erros comuns conforme seção 1. A rota literal `statistics` deve ser distinguida da rota de identificador.

`Statistics = {total: integer >= 0, counts: object<string, integer >= 0>, asOf: DateTime}`. Organization agrupa por `DONOR`/`INSTITUTION`; Offer e Redistribution agrupam por seus estados, incluindo categorias com zero. Soma de `counts` igual a `total` na mesma consulta. Cada serviço aplica o escopo administrativo e faz sua própria agregação; BFF não consulta bancos.

```json
{
  "total": 3,
  "counts": {"AVAILABLE": 1, "RESERVED": 1, "COLLECTED": 0, "COMPLETED": 1, "CANCELLED": 0, "EXPIRED": 0},
  "asOf": "2026-10-20T14:10:00-03:00"
}
```

## 8. Portal BFF: catálogo público

Prefixo `/portal/v1`. Identidade determina `donorId`/`institutionId`; esses campos não são aceitos no corpo público de criação. Consultas próprias fixam o filtro da organização autenticada. Erros de domínio preservam status/code com mensagem segura. Endereços completos só são enviados ao participante quando necessários à operação, nunca na busca de ofertas.

`PortalOffer` é OfferSummary com links públicos; para o dono, acrescenta `pickupAddress` e `editVersion`. Respostas de criação/edição/cancelamento são adaptadas para PortalOffer. `PortalRedistribution` contém `id`, `status`, `offer: {description, quantity, unit} ou null`, `delivery: {status, estimatedArrival: DateTime ou null} ou null`, `cancellationStatus`, `failure: {code, message} ou null`. `null` significa etapa ainda não criada, não falha de dependência. Falha de consulta essencial retorna `503/504`.

| Método e caminho | Entrada | Origem | Sucesso |
|---|---|---|---|
| `GET /home` | page/size não aceitos | Oferta ou demanda própria + redistribuições próprias | `200 PortalHome` |
| `GET /offers` | page, size, category, city | Offer: apenas disponíveis não vencidas | `200 Page<PortalOffer>` |
| `GET /offers/{id}` | id | Offer, respeitando visibilidade | `200 PortalOffer` |
| `GET /my-offers` | page, size, status; doador | Offer + donorId autenticado | `200 Page<PortalOffer>` |
| `POST /offers` | OfferInput sem donorId; doador | Offer | `201 PortalOffer`, Location público |
| `PATCH /offers/{id}` | OfferPatch; doador; If-Match | Offer | `200 PortalOffer` |
| `POST /offers/{id}/cancellation` | reason; doador; If-Match | Offer | `200 PortalOffer` |
| `GET /demands` | page, size; instituição | Matching + institutionId autenticado | `200 Page<Demand>` |
| `GET /demands/{id}` | id; instituição dona | Matching | `200 Demand` |
| `POST /demands` | DemandInput sem institutionId; instituição | Matching | `201 Demand`, Location público |
| `GET /demands/{id}/matches` | id; instituição dona | Matching | `200 Matches` |
| `GET /redistributions` | page, size, status; participante | Redistribution com filtro próprio | `200 Page<PortalRedistribution>` |
| `POST /redistributions` | `{offerId: Id}`; instituição | Redistribution + institutionId autenticado | `202 {id, status}`, Location público |
| `GET /redistributions/{id}` | participante | Redistribution + Offer + Logistics | `200 PortalRedistribution` |
| `POST /redistributions/{id}/cancellation` | reason; participante | Redistribution | `202` ou `200 {id, status, cancellationStatus}`, Location público quando 202 |

Comandos herdam idempotência e erros específicos do endpoint de domínio correspondente. GETs podem retornar `404` quando o recurso não está no escopo; acessos com perfil errado retornam `403`. BFF não cria reserva nem executa compensação.

`PortalHome = {profile: DONOR ou INSTITUTION, offers: Page<PortalOffer> ou null, demands: Page<Demand> ou null, redistributions: Page<PortalRedistribution>}`. Home fixa page 1/size 5; `offers` é preenchido para doador, `demands` para instituição. Totais são de toda a consulta autorizada, não do tamanho da página.

Exemplo da mesma redistribuição apresentada no Admin abaixo:

```json
{
  "id": "red-456", "status": "IN_TRANSIT",
  "offer": {"description": "Hortaliças variadas", "quantity": 30, "unit": "KG"},
  "delivery": {"status": "IN_TRANSIT", "estimatedArrival": "2026-10-20T16:30:00-03:00"},
  "cancellationStatus": null, "failure": null
}
```

## 9. Admin BFF: catálogo público

Prefixo `/admin/v1`. Consultas exigem perfil administrativo. Escritas de cadastro exigem permissão `organizations:manage`; comandos de agendamento exigem `logistics:schedule`; coleta, departure, entrega e failure exigem `logistics:operate` e atribuição à operação. Um operador com essas permissões pode usar uma área restrita do mesmo painel, sem receber acesso aos demais endpoints administrativos. O Gateway verifica essas permissões por rota; Logistics verifica a atribuição.

| Método e caminho | Entrada | Origem | Sucesso |
|---|---|---|---|
| `GET /dashboard` | sem parâmetros | Três endpoints statistics | `200 AdminDashboard` |
| `GET /organizations` | page, size, type, active | Organization | `200 Page<Organization>` |
| `GET /organizations/{id}` | id | Organization | `200 Organization`, ETag |
| `POST /organizations` | OrganizationInput | Organization | `201 Organization`, Location público |
| `PATCH /organizations/{id}` | OrganizationPatch; If-Match | Organization | `200 Organization`, ETag |
| `GET /redistributions` | page, size, status, donorId, institutionId | Redistribution | `200 Page<Redistribution>` |
| `GET /redistributions/{id}` | id | Redistribution | `200 Redistribution` |
| `GET /logistics/failures` | page, size | Logistics com status FAILED fixo | `200 Page<Logistics>` |
| `GET /logistics` | page, size, status, redistributionId | Logistics | `200 Page<Logistics>` |
| `GET /logistics/{id}` | id; admin ou operador atribuído | Logistics | `200 Logistics` |
| `POST /logistics/{id}/scheduling` | operatorId, scheduledAt | Logistics | `200 Logistics` |
| `POST /logistics/{id}/collection` | occurredAt | Logistics | `200 Logistics` |
| `POST /logistics/{id}/departure` | occurredAt | Logistics | `200 Logistics` |
| `POST /logistics/{id}/delivery` | occurredAt | Logistics | `200 Logistics` |
| `POST /logistics/{id}/failure` | code, message, occurredAt | Logistics | `200 Logistics` |
| `GET /offers/expired` | page, size | Offer com status EXPIRED fixo | `200 Page<OfferSummary>` |

Tipos de entrada e erros são os das operações internas correspondentes; POST exige Idempotency-Key. Acesso administrativo não oferece endpoint para forçar reserva, conclusão, liberação ou compensação. Links de ofertas no Admin são omitidos quando não houver rota pública equivalente.

`AdminDashboard = {organizations: Statistics, offers: Statistics, redistributions: Statistics}`. Cada componente conserva seu `asOf`; o dashboard não promete snapshot distribuído único. Se uma consulta essencial falhar, retornar `503/504` em vez de inventar totais.

Resposta administrativa da mesma redistribuição:

```json
{
  "id": "red-456", "status": "IN_TRANSIT", "offerId": "offer-123",
  "donorId": "org-123", "institutionId": "org-789", "reservationId": "res-123",
  "logisticsId": "log-321", "sagaState": "WAITING_DELIVERY", "lastEvent": "FoodCollected",
  "correlationId": "corr-999", "cancellationStatus": null,
  "createdAt": "2026-10-20T12:00:00-03:00", "updatedAt": "2026-10-20T14:05:00-03:00"
}
```

## 10. Roteamento e limites entre componentes

| Entrada no Gateway | Destino | Política |
|---|---|---|
| `/portal/v1/**` | Portal BFF, preservando caminho | Usuário autenticado; permissões de doador/instituição por operação |
| `/admin/v1/**` | Admin BFF, preservando caminho | Perfil/permissão específica de consulta ou operação |
| `/api/v1/**` | Sem destino público | `404`; endpoints de domínio só na rede interna |
| Outra versão ou rota inexistente | Sem destino | `404` |

O BFF resolve `/api/v1/organizations/**` no Organization; `/offers/**` no Offer; `/demands/**` no Demand/Matching; `/redistributions/**` no Redistribution; `/logistics/**` no Logistics. URLs-base são configuração de implantação, não endereços fixos no contrato. BFFs só encaminham métodos/caminhos catalogados, sem proxy arbitrário fornecido pelo cliente.

Os cenários de concorrência, idempotência, autorização e compensação da seção 15 do documento principal fazem parte dos critérios de aceitação destes contratos. A revisão cruzada deve alinhar os novos detalhes com os documentos 03 e 04 quando estiverem disponíveis.
