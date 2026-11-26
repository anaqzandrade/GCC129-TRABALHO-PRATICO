package com.ravita.redistribution.service;

import com.ravita.redistribution.client.ServiceClients;
import com.ravita.redistribution.config.RabbitMQConfig;
import com.ravita.redistribution.dto.CreateRedistributionRequest;
import com.ravita.redistribution.dto.LogisticsEvent;
import com.ravita.redistribution.dto.RedistributionStatistics;
import com.ravita.redistribution.dto.RedistributionCreatedEvent;
import com.ravita.redistribution.model.Redistribution;
import com.ravita.redistribution.model.RedistributionStatus;
import com.ravita.redistribution.repository.RedistributionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Orquestrador da SAGA de redistribuição (doc 04):
 * T1 cria a redistribuição, T2 reserva a oferta no offer-service, T3 cria a operação no logistics-service.
 * Se T3 falha, a compensação C2 libera a reserva feita em T2.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedistributionSagaService {

    private static final int COMPENSATION_ATTEMPTS = 3;
    private static final Pattern ERROR_CODE = Pattern.compile("\"code\"\\s*:\\s*\"([^\"]+)\"");
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<RedistributionStatus> TERMINAL =
            EnumSet.of(RedistributionStatus.COMPLETED, RedistributionStatus.FAILED, RedistributionStatus.CANCELLED);

    private final RedistributionRepository repository;
    private final ServiceClients clients;
    private final RabbitTemplate rabbitTemplate;

    @Value("${ravita.rabbitmq.exchange}")
    private String exchange;

    /**
     * @param correlationId X-Correlation-ID recebido (o gateway sempre envia); sem ele, um novo é gerado.
     *                      Fica gravado na redistribuição e é repassado nas chamadas ao offer e ao logistics.
     */
    public Redistribution createAndStartSaga(CreateRedistributionRequest req, String correlationId) {
        if (isBlank(req.offerId()) || isBlank(req.institutionId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "offerId e institutionId são obrigatórios");
        }
        String correlation = isBlank(correlationId) ? UUID.randomUUID().toString() : correlationId;

        // T1: criar a redistribuição (CREATED)
        Redistribution redistribution = Redistribution.builder()
                .offerId(req.offerId())
                .institutionId(req.institutionId())
                .correlationId(correlation)
                .lastEvent("RedistributionCreated")
                .status(RedistributionStatus.CREATED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        redistribution = repository.save(redistribution);
        log.info("SAGA {}: redistribuição criada para a oferta {}", redistribution.getId(), req.offerId());

        rabbitTemplate.convertAndSend(exchange, "redistribution.created",
                new RedistributionCreatedEvent("RedistributionCreated", redistribution.getId(),
                        redistribution.getOfferId(), redistribution.getInstitutionId(), LocalDateTime.now()));

        // T2: reservar a oferta no offer-service
        try {
            String reservationId = clients.reserveOffer(req.offerId(), redistribution.getId(), correlation);
            redistribution.setReservationId(reservationId);
            redistribution.setStatus(RedistributionStatus.OFFER_RESERVED);
            redistribution.setLastEvent("OfferReserved");
            redistribution.setUpdatedAt(LocalDateTime.now());
            redistribution = repository.save(redistribution);
            log.info("SAGA {}: T2 ok, oferta {} reservada (reserva {})",
                    redistribution.getId(), req.offerId(), reservationId);
        } catch (Exception e) {
            // nada foi reservado: não há o que compensar
            log.warn("SAGA {}: T2 falhou ao reservar a oferta {}: {}", redistribution.getId(), req.offerId(), describe(e));
            return fail(redistribution, "Falha ao reservar a oferta no offer-service: " + describe(e),
                    "OfferReservationFailed");
        }

        // T3: criar a operação no logistics-service
        try {
            UUID logisticsId = clients.createLogistics(
                    redistribution.getId(), req.offerId(), req.pickupAddress(), req.deliveryAddress(), correlation
            );
            redistribution.setLogisticsId(logisticsId);
            redistribution.setStatus(RedistributionStatus.LOGISTICS_PENDING);
            redistribution.setLastEvent("LogisticsScheduled");
            redistribution.setUpdatedAt(LocalDateTime.now());
            log.info("SAGA {}: T3 ok, operação logística {} criada", redistribution.getId(), logisticsId);
            return repository.save(redistribution);
        } catch (Exception e) {
            log.warn("SAGA {}: T3 falhou ({}). Executando a compensação C2: liberar a reserva {}",
                    redistribution.getId(), describe(e), redistribution.getReservationId());
            boolean released = releaseReservation(redistribution);
            String reason = "Falha ao criar a operação logística (" + describe(e) + "); "
                    + (released ? "compensação executada: reserva da oferta liberada"
                                : "compensação pendente: a reserva da oferta não pôde ser liberada");
            return fail(redistribution, reason, released ? "ReservationReleased" : "CompensationPending");
        }
    }

    /** Listagem paginada para os BFFs; page começa em 1, como no resto do contrato. */
    public Page<Redistribution> list(RedistributionStatus status, String institutionId, int page, int size) {
        if (page < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page deve ser maior ou igual a 1");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size deve estar entre 1 e " + MAX_PAGE_SIZE);
        }
        PageRequest pageable = PageRequest.of(page - 1, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        String institution = isBlank(institutionId) ? null : institutionId;
        if (status != null && institution != null) {
            return repository.findByStatusAndInstitutionId(status, institution, pageable);
        }
        if (status != null) {
            return repository.findByStatus(status, pageable);
        }
        if (institution != null) {
            return repository.findByInstitutionId(institution, pageable);
        }
        return repository.findAll(pageable);
    }

    /** Contagem por status; status sem redistribuições aparecem com zero. */
    public RedistributionStatistics statistics() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (RedistributionStatus status : RedistributionStatus.values()) {
            byStatus.put(status.name(), 0L);
        }
        repository.countByStatus().forEach(row -> byStatus.put(row.getStatus().name(), row.getTotal()));
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        return new RedistributionStatistics(total, byStatus);
    }

    public Redistribution getById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Redistribuição não encontrada"));
    }

    public Redistribution cancel(UUID id) {
        Redistribution redistribution = getById(id);
        if (redistribution.getStatus() == RedistributionStatus.COMPLETED ||
            redistribution.getStatus() == RedistributionStatus.DELIVERED ||
            redistribution.getStatus() == RedistributionStatus.IN_TRANSIT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Não é possível cancelar redistribuição já coletada ou entregue");
        }

        if (redistribution.getStatus() == RedistributionStatus.OFFER_RESERVED ||
            redistribution.getStatus() == RedistributionStatus.LOGISTICS_PENDING) {
            releaseReservation(redistribution);
        }

        redistribution.setStatus(RedistributionStatus.CANCELLED);
        redistribution.setLastEvent("RedistributionCancelled");
        redistribution.setUpdatedAt(LocalDateTime.now());
        return repository.save(redistribution);
    }

    @RabbitListener(queues = RabbitMQConfig.COLLECTED_QUEUE)
    @Transactional
    public void onFoodCollected(LogisticsEvent event) {
        log.info("Evento FoodCollected recebido para redistribuição {}", event.redistributionId());
        repository.findById(event.redistributionId()).ifPresent(r -> {
            if (TERMINAL.contains(r.getStatus())) {
                log.info("Redistribuição {} já está {}; evento ignorado", r.getId(), r.getStatus());
                return;
            }
            r.setStatus(RedistributionStatus.IN_TRANSIT);
            r.setLastEvent("FoodCollected");
            r.setUpdatedAt(LocalDateTime.now());
            repository.save(r);
        });
    }

    @RabbitListener(queues = RabbitMQConfig.DELIVERED_QUEUE)
    @Transactional
    public void onFoodDelivered(LogisticsEvent event) {
        log.info("Evento FoodDelivered recebido para redistribuição {}", event.redistributionId());
        repository.findById(event.redistributionId()).ifPresent(r -> {
            if (TERMINAL.contains(r.getStatus())) {
                log.info("Redistribuição {} já está {}; evento ignorado", r.getId(), r.getStatus());
                return;
            }
            r.setStatus(RedistributionStatus.DELIVERED);
            r.setLastEvent("FoodDelivered");
            r.setUpdatedAt(LocalDateTime.now());
            repository.save(r);

            // Finaliza a SAGA
            r.setStatus(RedistributionStatus.COMPLETED);
            r.setUpdatedAt(LocalDateTime.now());
            repository.save(r);
        });
    }

    /** C2: libera a reserva no offer-service, com novas tentativas. A liberação é idempotente. */
    private boolean releaseReservation(Redistribution redistribution) {
        if (redistribution.getReservationId() == null) {
            return true;
        }
        for (int attempt = 1; attempt <= COMPENSATION_ATTEMPTS; attempt++) {
            try {
                clients.releaseOffer(redistribution.getOfferId(), redistribution.getReservationId(),
                        redistribution.getCorrelationId());
                log.info("SAGA {}: compensação ok, reserva {} liberada",
                        redistribution.getId(), redistribution.getReservationId());
                return true;
            } catch (Exception e) {
                log.warn("SAGA {}: tentativa {} de liberar a reserva {} falhou: {}",
                        redistribution.getId(), attempt, redistribution.getReservationId(), describe(e));
                pause(500L * attempt);
            }
        }
        log.error("SAGA {}: compensação não concluída; a reserva {} continua ativa",
                redistribution.getId(), redistribution.getReservationId());
        return false;
    }

    private Redistribution fail(Redistribution redistribution, String reason, String lastEvent) {
        redistribution.setStatus(RedistributionStatus.FAILED);
        redistribution.setFailureReason(reason);
        redistribution.setLastEvent(lastEvent);
        redistribution.setUpdatedAt(LocalDateTime.now());
        return repository.save(redistribution);
    }

    /** Resumo do erro para o failureReason: status e código do contrato, quando houver. */
    private static String describe(Exception e) {
        if (e instanceof RestClientResponseException response) {
            Matcher code = ERROR_CODE.matcher(response.getResponseBodyAsString());
            return response.getStatusCode().value() + (code.find() ? " " + code.group(1) : "");
        }
        return e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
