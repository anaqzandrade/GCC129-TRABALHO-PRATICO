## 06. API Gateway e BFFs: como foram implementados

---

## 1. Objetivo

O doc 02 define o contrato REST, o gateway e os BFFs. Este documento registra a implementação: rotas, filtros,
autenticação, rate limiting, os endpoints dos dois BFFs e a mesma redistribuição vista pelos dois.

```
navegador ──► api-gateway :8000 ──► /app/**       ──► web (páginas do portal e do admin)
                                ──► /portal/v1/** ──► portal-bff ──► serviços de domínio e assistente
                                ──► /admin/v1/**  ──► admin-bff  ──► serviços de domínio
```

O gateway é a única porta publicada no host e fica só na rede `bff`. Ele não alcança os serviços de domínio: quem
chama os serviços são os BFFs, que ficam nas redes `bff` e `services` (doc 05, seção 8).

---

## 2. API Gateway

Spring Cloud Gateway 2025.0 (WebFlux), módulo `api-gateway/`.

### Rotas

| Rota | Destino | Autorização |
|---|---|---|
| `POST /auth/token` | o próprio gateway (`AuthController`) | pública |
| `GET /` | redireciona para `/app/portal/` | pública |
| `GET /app/**` | `web:80`, sem o prefixo `/app` (`StripPrefix=1`) | pública: as páginas não têm dados; os dados exigem token |
| `/portal/v1/**` | `portal-bff:8091` | papel `DONOR`, `INSTITUTION` ou `ADMIN` |
| `/admin/v1/**` | `admin-bff:8092` | papel `ADMIN` |
| qualquer outra, inclusive `/api/v1/**` | — | negada (`401` sem token, `403` com token) |

As páginas e a API ficam na mesma origem (`localhost:8000`), então o navegador não precisa de CORS.

### Filtros, na ordem em que agem

| Filtro | O que faz |
|---|---|
| `CorrelationIdWebFilter` | Usa o `X-Correlation-ID` recebido ou gera um; devolve na resposta e registra uma linha de log por requisição com método, caminho, status, tempo e o id |
| `RateLimitWebFilter` | Token bucket em memória: 20 requisições de rajada, recarga de 2 por segundo. A chave é o hash do token ou, sem token, o IP. Estourou: `429 RATE_LIMITED` com `Retry-After`. Health e arquivos das páginas não contam |
| Spring Security | Valida o JWT (assinatura HS256, emissor `ravita-gateway`, validade) e o papel exigido pela rota |
| `IdentityHeadersFilter` | Remove `X-User-Id`, `X-Org-Id` e `X-Roles` que vierem do cliente e preenche esses cabeçalhos a partir do token; os BFFs confiam só neles |

### Autenticação

`POST /auth/token` com `{"role": "DONOR", "organizationId": "org-..."}` devolve:

```json
{ "accessToken": "eyJ...", "tokenType": "Bearer", "expiresIn": 7200, "role": "DONOR", "organizationId": "org-..." }
```

- Claims: `sub` (`donor:org-...`, `institution:org-...` ou `admin`), `orgId` e `roles`; validade de 2 horas.
- `organizationId` é obrigatório para `DONOR` e `INSTITUTION`. Papel desconhecido: `400 INVALID_ROLE`.
- A chave de assinatura vem de `JWT_SECRET` (`.env`), com no mínimo 32 caracteres.
- É uma emissão simplificada para o trabalho: o gateway não confere senha. Em produção, o token viria de um
  provedor de identidade, e o gateway continuaria validando do mesmo jeito.

### Erros do próprio gateway

Todos no formato do contrato, `{code, message, correlationId}`:

| Situação | Resposta |
|---|---|
| Sem token ou token inválido | `401 UNAUTHENTICATED` |
| Papel sem acesso à rota | `403 FORBIDDEN` |
| Limite de requisições | `429 RATE_LIMITED` |
| BFF fora do ar (conexão em até 2 s) | `503 SERVICE_UNAVAILABLE` |
| BFF sem resposta em 15 s | `504` |

O cliente HTTP do gateway guarda a resolução de nomes por no máximo 5 s (`HttpClientConfig`). Sem isso, um BFF
recriado com outro IP ficaria inacessível por até 10 minutos, porque o DNS do Docker responde com validade de
600 s.

---

## 3. BFFs

Spring MVC com `RestClient`, módulos `portal-bff/` e `admin-bff/`. Não têm banco nem alteram estado por conta
própria: chamam as APIs dos serviços e montam a resposta para a tela.

### Comum aos dois

- Repassam a cada serviço o `X-Correlation-ID` e os cabeçalhos de identidade, e registram o id em cada linha de log.
- Conferem o papel de novo (defesa em profundidade) e, no Portal, a posse do recurso: recurso de outra organização
  responde `404`, para não revelar que existe.
- Falhas dos serviços:

| Situação | Resposta |
|---|---|
| Serviço fora do ar: conexão recusada, nome que não resolve ou conexão que não abre em 2 s | `503 DEPENDENCY_UNAVAILABLE`, nunca uma lista vazia |
| Serviço aceitou a conexão, mas não respondeu no prazo de leitura | `504 DEPENDENCY_TIMEOUT` |
| Erro de negócio do serviço (`409`, `422`...) | Repassado como veio |

- Páginas no formato do contrato, `{items, page, size, total}`, com a página começando em 1, qualquer que seja o
  formato do serviço de origem.
- Cache de DNS de 5 s, pelo mesmo motivo do gateway.

### Portal BFF (`/portal/v1`), para doadores e instituições

| Rota | Quem | Serviços chamados |
|---|---|---|
| `GET /offers`, `GET /offers/{id}` | todos | offer (a lista vem do modelo de leitura do CQRS) |
| `GET /my-offers`, `POST /offers` | `DONOR` (o `donorId` vem do token) | offer |
| `POST /demands`, `GET /demands`, `GET /demands/{id}`, `GET /demands/{id}/matches` | `INSTITUTION` (lista também para `ADMIN`) | matching |
| `POST /redistributions` | `INSTITUTION`, só com `{offerId}` | offer, organization, redistribution |
| `GET /redistributions`, `GET /redistributions/{id}` | participantes e `ADMIN` | redistribution, offer, logistics |
| `POST /redistributions/{id}/cancellation` | instituição dona | redistribution |
| `POST /assistant/questions` | todos | assistant (doc 07) |

### Admin BFF (`/admin/v1`), para a equipe da Ravita

| Rota | Serviços chamados |
|---|---|
| `GET /dashboard` | organization, offer, redistribution |
| `GET /organizations`, `POST /organizations`, `GET /organizations/{id}` | organization |
| `GET /redistributions?status=`, `GET /redistributions/{id}` | redistribution, offer, logistics |
| `GET /logistics?status=`, `POST /logistics/{id}/collection`, `POST /logistics/{id}/delivery` | logistics |

Registrar a coleta ou a entrega não altera a redistribuição diretamente: o logistics publica o evento e a SAGA
avança por ele (doc 04).

---

## 4. A mesma redistribuição nos dois BFFs

Saída real da verificação da etapa 7, com a coleta já agendada.

**Portal** (`GET /portal/v1/redistributions/{id}`, como a instituição vê):

```json
{
  "id": "213639c9-dc51-45fe-a0fd-1cb0558f870e",
  "status": "LOGISTICS_PENDING",
  "statusDescription": "Coleta agendada",
  "offer": { "id": "offer-45f22515-...", "description": "Hortaliças variadas", "foodCategory": "VEGETABLES",
             "quantity": 30, "unit": "KG", "city": "Lavras" },
  "delivery": { "status": "SCHEDULED", "pickupAddress": "Rua Central, 100 - Centro, Lavras/MG",
                "deliveryAddress": "Rua das Flores, 50 - Centro, Lavras/MG" },
  "_links": { "self": {...}, "offer": {...}, "cancel": { "href": ".../cancellation", "method": "POST" } }
}
```

**Admin** (`GET /admin/v1/redistributions/{id}`, como a equipe diagnostica):

```json
{
  "id": "213639c9-dc51-45fe-a0fd-1cb0558f870e",
  "status": "LOGISTICS_PENDING",
  "sagaState": "LOGISTICS_SCHEDULED",
  "compensation": "NONE",
  "lastEvent": "LogisticsScheduled",
  "correlationId": "etapa7-saga-ok",
  "offerId": "offer-45f22515-...", "donorId": "org-d0d845a8-...", "institutionId": "org-ccf70381-...",
  "reservationId": "res-3a43d2df-...", "logisticsId": "5474960d-...",
  "offer": { "status": "RESERVED", "description": "Hortaliças variadas" },
  "logistics": { "status": "SCHEDULED" }
}
```

| | Portal | Admin |
|---|---|---|
| Linguagem | orientada à tarefa: "Coleta agendada", endereços formatados | estados internos: `sagaState`, `lastEvent`, `compensation` |
| Identificadores | só o da redistribuição e o da oferta | todos: reserva, logística, doador, instituição |
| Diagnóstico | numa falha, `problem` em linguagem simples | `failureReason` técnico e `correlationId` para seguir nos logs |
| Ações | link `cancel` enquanto a coleta não aconteceu | — (a coleta e a entrega ficam na área de logística) |

As duas respostas saem dos mesmos três serviços (redistribution, offer e logistics); só a montagem muda. É o motivo
de ter dois BFFs em vez de uma API genérica.

---

## 5. Clientes

Páginas estáticas no container `web` (nginx), servidas pelo gateway:

| Página | Endereço | O que faz |
|---|---|---|
| Portal | http://localhost:8000/app/portal/ | Entra como doador ou instituição; lista e publica ofertas; inicia e acompanha redistribuições; pergunta ao assistente |
| Admin | http://localhost:8000/app/admin/ | Entra como admin; dashboard; redistribuições com filtro por status e diagnóstico; coleta e entrega; cadastro de organizações |

Cada página fala só com o seu BFF. O token fica no `sessionStorage` da aba e vai no cabeçalho `Authorization`; cada
chamada leva um `X-Correlation-ID` novo, que aparece nas mensagens de erro.

---

## 6. Como verificar

- `scripts/verificacao.ps1`: confere portas, redes, gateway, BFFs, SAGA, CQRS, outbox e assistente, tudo pela
  porta 8000.
- Coleção do Postman `postman/Ravita - Pelo gateway.postman_collection.json`.
- Testes: `ApiGatewayTest` e `IdentityHeadersFilterTest` no gateway; `PortalViewsTest`, `AdminViewsTest` e os
  testes web dos dois BFFs, que mostram o 403 por papel e o 503 com os serviços fora do ar.
