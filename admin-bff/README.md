# Admin BFF

Backend for Frontend do painel administrativo da Ravita (doc 02, seções 12 a 14). Mostra o sistema como a
equipe precisa vê-lo: contagens, o estado da SAGA de cada redistribuição, a compensação e o correlation id
para seguir uma requisição nos logs. Não tem banco e não altera estado por conta própria.

- Java 21, Spring Boot 3.5.7 (Spring MVC + `RestClient`)
- Porta **8092**, sem porta publicada no compose: só o gateway o alcança, pela rede `bff`
- Todas as rotas exigem o papel `ADMIN`. O gateway já confere; o BFF confere de novo

## Rotas (`/admin/v1`)

| Rota | O que faz | Serviços chamados |
|---|---|---|
| `GET /dashboard` | Doadores, instituições, ofertas e redistribuições por estado | organization, offer, redistribution |
| `GET /organizations?type=&active=&page=&size=` | Lista de organizações | organization |
| `POST /organizations` | Cadastra doador ou instituição | organization |
| `GET /organizations/{id}` | Detalhe da organização | organization |
| `GET /redistributions?status=&institutionId=` | Acompanhamento da SAGA; `status=FAILED` lista as falhas | redistribution |
| `GET /redistributions/{id}` | Diagnóstico completo de uma redistribuição | redistribution, offer, logistics |
| `GET /logistics?status=` | Operações logísticas; `status=FAILED` lista as falhas logísticas | logistics |
| `POST /logistics/{id}/collection` | Registra a coleta; a SAGA avança para `IN_TRANSIT` pelo evento | logistics |
| `POST /logistics/{id}/delivery` | Registra a entrega; a SAGA conclui pelo evento | logistics |

## A mesma redistribuição no Admin

```json
{
  "id": "red-...",
  "status": "FAILED",
  "sagaState": "FAILED",
  "compensation": "DONE",
  "lastEvent": "ReservationReleased",
  "correlationId": "demo-saga-1",
  "failureReason": "Falha ao criar a operação logística (503 ...); compensação executada: reserva da oferta liberada",
  "offerId": "offer-...", "donorId": "org-...", "institutionId": "org-...",
  "reservationId": "res-...", "logisticsId": null,
  "offer": { "status": "AVAILABLE", "description": "...", "updatedAt": "..." },
  "logistics": null
}
```

`sagaState` segue os estados do doc 04 (`LOGISTICS_PENDING` aparece como `LOGISTICS_SCHEDULED`).
`compensation` diz se a reserva precisou ser desfeita: `DONE` (liberada), `PENDING` (a liberação falhou),
`NOT_NEEDED` (falhou antes de reservar) ou `NONE`.

## Falhas dos serviços

Serviço fora do ar (conexão recusada, nome que não resolve ou conexão que não abre em 2 s): `503 DEPENDENCY_UNAVAILABLE`.
Serviço que aceitou a conexão, mas não respondeu em 5 s: `504 DEPENDENCY_TIMEOUT`. Erros de negócio
dos serviços são repassados como vieram. Toda resposta de erro leva `correlationId`.

## Testes

```powershell
docker run --rm -v "${PWD}/admin-bff:/app" -w /app maven:3.9.9-eclipse-temurin-21-alpine mvn -q test
```
