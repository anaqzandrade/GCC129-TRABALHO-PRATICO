# API Gateway

Única entrada externa da Ravita (doc 02, seções 10 e 11). Encaminha as rotas públicas para os BFFs e
concentra o que vale para toda requisição. Regra de negócio fica fora dele.

- Java 21, Spring Boot 3.5.7, Spring Cloud 2025.0 (Spring Cloud Gateway sobre WebFlux)
- Porta **8000**

## Rotas

| Rota externa | Destino | Quem pode |
|---|---|---|
| `POST /auth/token` | o próprio gateway (token de desenvolvimento) | qualquer um |
| `/portal/v1/**` | Portal BFF (`portal-bff:8091`) | `DONOR`, `INSTITUTION` ou `ADMIN` |
| `/admin/v1/**` | Admin BFF (`admin-bff:8092`) | só `ADMIN` |
| qualquer outra | — | ninguém (401 sem token, 403 com token) |

As rotas `/api/v1/**` dos serviços não passam pelo gateway. No compose, ele só está na rede `bff`:
não alcança os serviços de domínio, só os BFFs.

## Responsabilidades

| Responsabilidade | Onde | Como |
|---|---|---|
| Correlation ID | `CorrelationIdWebFilter` | Usa o `X-Correlation-ID` recebido ou gera um; repassa ao BFF e devolve na resposta. Primeiro filtro de todos |
| Log | `CorrelationIdWebFilter` | Uma linha por requisição: método, caminho, status, duração e correlation id |
| Rate limiting | `RateLimitWebFilter` | Token bucket por cliente (token ou IP): 20 de rajada, recarga de 2 por segundo. Sem ficha: `429 RATE_LIMITED` e `Retry-After` |
| Autenticação | `SecurityConfig` | JWT HS256 assinado com `JWT_SECRET`; confere assinatura, emissor e validade. Sem token válido: `401 UNAUTHENTICATED` |
| Autorização por rota | `SecurityConfig` | A claim `roles` do token decide a rota. Papel errado: `403 FORBIDDEN` |
| Identidade para os BFFs | `IdentityHeadersFilter` | Descarta `X-User-Id`, `X-Org-Id` e `X-Roles` vindos do cliente e preenche de novo a partir do token |
| Erros | `GatewayErrorHandler` | BFF fora do ar: `503 SERVICE_UNAVAILABLE`; sem resposta em 15 s: `504`. Sempre `{code, message, correlationId}` |

O rate limiting fica em memória, o que basta para uma instância do gateway. Com várias réplicas,
o balde precisaria ficar num armazenamento compartilhado, como o Redis.

## Token de desenvolvimento

`POST /auth/token` faz o papel de um provedor de identidade (Keycloak, Auth0...) para a demonstração:
não pede senha, recebe o papel e a organização e devolve um JWT válido por 2 h. Em produção, esse
endpoint não existiria; o gateway só validaria os tokens do provedor.

```json
{ "role": "DONOR", "organizationId": "org-..." }
```

`role` é `DONOR`, `INSTITUTION` ou `ADMIN`; `organizationId` é obrigatório para os dois primeiros.

## Testar (PowerShell, com o compose no ar)

```powershell
$t = (Invoke-RestMethod -Method Post http://localhost:8000/auth/token -ContentType 'application/json' -Body '{"role":"DONOR","organizationId":"org-123"}').accessToken

curl.exe -i http://localhost:8000/portal/v1/offers                                  # 401 UNAUTHENTICATED
curl.exe -i -H "Authorization: Bearer $t" http://localhost:8000/admin/v1/dashboard  # 403 FORBIDDEN
curl.exe -i -H "Authorization: Bearer $t" http://localhost:8000/portal/v1/offers    # 503 enquanto o Portal BFF não existe
curl.exe -i -H "X-Correlation-ID: demo-1" http://localhost:8000/portal/v1/offers    # devolve X-Correlation-ID: demo-1
1..25 | ForEach-Object { curl.exe -s -o NUL -w "%{http_code} " http://localhost:8000/portal/v1/offers }   # 401 ... e depois 429
docker compose logs api-gateway --since 2m                                           # uma linha por requisição
```

No Windows, use `curl.exe`: no PowerShell 5.1, `curl` é outro comando.
