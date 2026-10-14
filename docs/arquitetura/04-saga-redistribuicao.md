## 04. SAGA de Redistribuição

---

## 1. Objetivo

Modelar a principal transação distribuída da Ravita utilizando SAGA.

A transação escolhida é a criação de uma redistribuição e envolve pelo menos:

- Redistribution Service;
- Offer Service;
- Logistics Service.

O Organization Service participa de validações, mas não precisa compor uma transação compensável da SAGA.

---

## 2. Variante escolhida

A Ravita utilizará **SAGA orquestrada**.

O **Redistribution Service** será o orquestrador.

---

## 3. Justificativa

A SAGA orquestrada foi escolhida porque o fluxo possui uma sequência clara:

1. criar redistribuição;
2. reservar oferta;
3. agendar logística;
4. acompanhar entrega;
5. concluir redistribuição.

Um orquestrador explícito facilita:

- acompanhar o estado global;
- controlar compensações;
- demonstrar o fluxo academicamente;
- diagnosticar falhas;
- evitar lógica de coordenação espalhada por vários serviços.

Na alternativa coreografada, cada serviço reagiria a eventos e decidiria o próximo passo. Para o escopo da Ravita, isso aumentaria a dificuldade de acompanhar e demonstrar a transação completa.

---

## 4. Estados da SAGA

```text
CREATED
RESERVING_OFFER
OFFER_RESERVED
SCHEDULING_LOGISTICS
LOGISTICS_SCHEDULED
IN_TRANSIT
DELIVERED
COMPLETED
COMPENSATING
CANCELLED
FAILED
```

---

## 5. Pré-condições

Antes do primeiro passo compensável:

- a oferta deve existir;
- a instituição deve existir;
- a instituição deve estar ativa;
- a solicitação deve possuir os identificadores necessários.

A validação da organização pode ocorrer antes do início das transações compensáveis.

---

## 6. Transações locais

### T1 — Criar redistribuição

**Serviço:** Redistribution Service

```text
Redistribution.status = CREATED
```

Compensação:

```text
CancelRedistribution
```

### T2 — Reservar oferta

**Serviço:** Offer Service

Sucesso:

```text
Offer → RESERVED
```

Falhas possíveis:

- oferta já reservada;
- oferta cancelada;
- oferta expirada;
- quantidade indisponível.

Compensação:

```text
ReleaseOffer
```

Resultado, se a oferta ainda estiver válida:

```text
Offer → AVAILABLE
```

### T3 — Agendar logística

**Serviço:** Logistics Service

Sucesso:

```text
Logistics → SCHEDULED
```

Compensação:

```text
CancelLogistics
```

---

