package com.ravita.redistribution.service;

import com.ravita.redistribution.client.ServiceClients;
import com.ravita.redistribution.config.RabbitMQConfig;
import com.ravita.redistribution.dto.CreateRedistributionRequest;
import com.ravita.redistribution.dto.LogisticsEvent;
import com.ravita.redistribution.dto.RedistributionCreatedEvent;
import com.ravita.redistribution.model.Redistribution;
import com.ravita.redistribution.model.RedistributionStatus;
import com.ravita.redistribution.repository.RedistributionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedistributionSagaService {

    private final RedistributionRepository repository;
    private final ServiceClients clients;
    private final RabbitTemplate rabbitTemplate;

    @Value("${ravita.rabbitmq.exchange}")
    private String exchange;

    public Redistribution createAndStartSaga(CreateRedistributionRequest req) {
        // Passo 1: Criar redistribuição (CREATED)
        Redistribution redistribution = Redistribution.builder()
                .offerId(req.offerId())
                .institutionId(req.institutionId())
                .status(RedistributionStatus.CREATED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        redistribution = repository.save(redistribution);

        rabbitTemplate.convertAndSend(exchange, "redistribution.created",
                new RedistributionCreatedEvent("RedistributionCreated", redistribution.getId(),
                        redistribution.getOfferId(), redistribution.getInstitutionId(), LocalDateTime.now()));

        // Passo 2: Reservar oferta no Offer Service
        try {
            clients.reserveOffer(req.offerId());
            redistribution.setStatus(RedistributionStatus.OFFER_RESERVED);
            redistribution.setUpdatedAt(LocalDateTime.now());
            redistribution = repository.save(redistribution);
        } catch (Exception e) {
            log.error("Falha ao reservar oferta {}: {}", req.offerId(), e.getMessage());
            redistribution.setStatus(RedistributionStatus.FAILED);
            redistribution.setFailureReason("Falha ao reservar oferta no Offer Service");
            redistribution.setUpdatedAt(LocalDateTime.now());
            return repository.save(redistribution);
        }

        // Passo 3: Criar operação no Logistics Service
        try {
            UUID logisticsId = clients.createLogistics(
                    redistribution.getId(), req.offerId(), req.pickupAddress(), req.deliveryAddress()
            );
            redistribution.setLogisticsId(logisticsId);
            redistribution.setStatus(RedistributionStatus.LOGISTICS_PENDING);
            redistribution.setUpdatedAt(LocalDateTime.now());
            return repository.save(redistribution);
        } catch (Exception e) {
            log.error("Falha ao criar logística. Executando compensação (release offer)...");
            try {
                clients.releaseOffer(req.offerId());
            } catch (Exception compEx) {
                log.error("Falha na compensação da oferta {}: {}", req.offerId(), compEx.getMessage());
            }
            redistribution.setStatus(RedistributionStatus.FAILED);
            redistribution.setFailureReason("Falha ao criar operação logística");
            redistribution.setUpdatedAt(LocalDateTime.now());
            return repository.save(redistribution);
        }
    }

    public Redistribution getById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Redistribuição não encontrada"));
    }

    public Redistribution cancel(UUID id) {
        Redistribution redistribution = getById(id);
        if (redistribution.getStatus() == RedistributionStatus.COMPLETED ||
            redistribution.getStatus() == RedistributionStatus.DELIVERED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Não é possível cancelar redistribuição já entregue");
        }

        if (redistribution.getStatus() == RedistributionStatus.OFFER_RESERVED ||
            redistribution.getStatus() == RedistributionStatus.LOGISTICS_PENDING) {
            try {
                clients.releaseOffer(redistribution.getOfferId());
            } catch (Exception e) {
                log.warn("Erro ao liberar oferta durante cancelamento: {}", e.getMessage());
            }
        }

        redistribution.setStatus(RedistributionStatus.CANCELLED);
        redistribution.setUpdatedAt(LocalDateTime.now());
        return repository.save(redistribution);
    }

    @RabbitListener(queues = RabbitMQConfig.COLLECTED_QUEUE)
    @Transactional
    public void onFoodCollected(LogisticsEvent event) {
        log.info("Evento FoodCollected recebido para redistribuição {}", event.redistributionId());
        repository.findById(event.redistributionId()).ifPresent(r -> {
            r.setStatus(RedistributionStatus.IN_TRANSIT);
            r.setUpdatedAt(LocalDateTime.now());
            repository.save(r);
        });
    }

    @RabbitListener(queues = RabbitMQConfig.DELIVERED_QUEUE)
    @Transactional
    public void onFoodDelivered(LogisticsEvent event) {
        log.info("Evento FoodDelivered recebido para redistribuição {}", event.redistributionId());
        repository.findById(event.redistributionId()).ifPresent(r -> {
            r.setStatus(RedistributionStatus.DELIVERED);
            r.setUpdatedAt(LocalDateTime.now());
            repository.save(r);

            // Finaliza a SAGA
            r.setStatus(RedistributionStatus.COMPLETED);
            r.setUpdatedAt(LocalDateTime.now());
            repository.save(r);
        });
    }
}