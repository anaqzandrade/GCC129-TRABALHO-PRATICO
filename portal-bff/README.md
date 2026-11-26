# Portal BFF

Backend for Frontend do portal de doadores e instituições (doc 02, seções 12 a 14). Recebe as requisições
do API Gateway, chama os serviços de domínio pela API REST e devolve respostas no formato da tela:
sem campos internos e com links para o próximo passo. Não tem banco e não altera estado por conta própria.

- Java 21, Spring Boot 3.5.7 (Spring MVC + `RestClient`)
- Porta **8091**, sem porta publicada no compose: só o gateway o alcança, pela rede `bff`

## Rotas (`/portal/v1`)

| Rota | Quem | O que faz | Serviços chamados |
|---|---|---|---|
| `GET /offers?category=&city=&page=&size=` | todos | Ofertas disponíveis | offer (modelo de leitura, CQRS) |
| `GET /offers/{id}` | todos | Detalhe da oferta | offer |
| `GET /my-offers` | `DONOR` | Ofertas do próprio doador | offer |
| `POST /offers` | `DONOR` | Publica uma oferta; o `donorId` vem do token | offer |
| `POST /demands` | `INSTITUTION` | Registra uma demanda; o `institutionId` vem do token | matching |
| `GET /demands` | `INSTITUTION`, `ADMIN` | Demandas da instituição (o admin vê todas) | matching |
| `GET /demands/{id}` e `/{id}/matches` | dona da demanda, `ADMIN` | Demanda e ofertas compatíveis | matching |
| `POST /redistributions` | `INSTITUTION` | Inicia a SAGA a partir de `{offerId}` | offer, organization, redistribution |
| `GET /redistributions` | `INSTITUTION`, `ADMIN` | Redistribuições da instituição | redistribution |
| `GET /redistributions/{id}` | participantes, `ADMIN` | Andamento, agregando três serviços | redistribution, offer, logistics |
| `POST /redistributions/{id}/cancellation` | instituição dona | Cancela enquanto a coleta não aconteceu | redistribution |
| `POST /assistant/questions` | todos | Pergunta ao assistente de doação; repassa só a pergunta (doc 07) | assistant |

A identidade chega nos cabeçalhos `X-User-Id`, `X-Org-Id` e `X-Roles`, preenchidos pelo gateway a partir
do token. Recurso de outra organização responde `404`, para não revelar que ele existe.

## Agregação e transformação

`GET /redistributions/{id}` junta três respostas em uma, orientada à tarefa da instituição:

```json
{
  "id": "red-...",
  "status": "IN_TRANSIT",
  "statusDescription": "Doação coletada e a caminho",
  "offer": { "id": "offer-...", "description": "Hortaliças variadas", "quantity": 30, "unit": "KG", "city": "Lavras" },
  "delivery": { "status": "COLLECTED", "pickupAddress": "...", "deliveryAddress": "...", "collectedAt": "..." },
  "_links": { "self": {...}, "offer": {...} }
}
```

O Portal não mostra `reservationId`, `logisticsId`, `lastEvent`, `correlationId` nem o motivo técnico da
falha: numa redistribuição `FAILED`, o campo `problem` explica o que houve em linguagem simples. O Admin BFF
mostra o mesmo recurso com esses dados (ver [admin-bff](../admin-bff/README.md)).

Em `POST /redistributions`, o BFF monta o pedido da SAGA: o endereço de coleta vem da oferta e o de entrega,
do cadastro da instituição no organization-service.

## Falhas dos serviços

| Situação | Resposta |
|---|---|
| Serviço fora do ar: conexão recusada, nome que não resolve ou conexão que não abre em 2 s | `503 DEPENDENCY_UNAVAILABLE`, nunca uma lista vazia |
| Serviço aceitou a conexão, mas não respondeu em 12 s | `504 DEPENDENCY_TIMEOUT` |
| Erro de negócio do serviço (`409`, `422`...) | Repassado como veio: `{code, message}` do serviço |

Toda resposta de erro leva `correlationId`. O `X-Correlation-ID` recebido do gateway é repassado a cada serviço
chamado e aparece em todas as linhas de log.

## Testes

```powershell
docker run --rm -v "${PWD}/portal-bff:/app" -w /app maven:3.9.9-eclipse-temurin-21-alpine mvn -q test
```
