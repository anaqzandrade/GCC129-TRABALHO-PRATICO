package com.ravita.logistics.service;

import com.ravita.logistics.dto.CreateLogisticsRequest;
import com.ravita.logistics.dto.LogisticsEvent;
import com.ravita.logistics.model.LogisticsOperation;
import com.ravita.logistics.model.LogisticsStatus;
import com.ravita.logistics.repository.LogisticsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LogisticsService {

    private final LogisticsRepository repository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${ravita.rabbitmq.exchange}")
    private String exchange;

    @Transactional
    public LogisticsOperation create(CreateLogisticsRequest request) {
        LogisticsOperation op = LogisticsOperation.builder()
                .redistributionId(request.redistributionId())
                .offerId(request.offerId())
                .pickupAddress(request.pickupAddress() != null ? request.pickupAddress() : "Endereço Doador")
                .deliveryAddress(request.deliveryAddress() != null ? request.deliveryAddress() : "Endereço Instituição")
                .status(LogisticsStatus.SCHEDULED)
                .createdAt(LocalDateTime.now())
                .build();
        return repository.save(op);
    }

    public LogisticsOperation getById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Operação logística não encontrada"));
    }

    @Transactional
    public LogisticsOperation collect(UUID id) {
        LogisticsOperation op = getById(id);
        if (op.getStatus() != LogisticsStatus.PENDING && op.getStatus() != LogisticsStatus.SCHEDULED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Operação não pode ser coletada no estado " + op.getStatus());
        }
        op.setStatus(LogisticsStatus.COLLECTED);
        op.setCollectedAt(LocalDateTime.now());
        LogisticsOperation saved = repository.save(op);

        LogisticsEvent event = new LogisticsEvent(
                "FoodCollected", saved.getId(), saved.getRedistributionId(), saved.getOfferId(), LocalDateTime.now()
        );
        rabbitTemplate.convertAndSend(exchange, "logistics.food.collected", event);
        return saved;
    }

    @Transactional
    public LogisticsOperation deliver(UUID id) {
        LogisticsOperation op = getById(id);
        if (op.getStatus() != LogisticsStatus.COLLECTED && op.getStatus() != LogisticsStatus.IN_TRANSIT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Operação não pode ser entregue no estado " + op.getStatus());
        }
        op.setStatus(LogisticsStatus.DELIVERED);
        op.setDeliveredAt(LocalDateTime.now());
        LogisticsOperation saved = repository.save(op);

        LogisticsEvent event = new LogisticsEvent(
                "FoodDelivered", saved.getId(), saved.getRedistributionId(), saved.getOfferId(), LocalDateTime.now()
        );
        rabbitTemplate.convertAndSend(exchange, "logistics.food.delivered", event);
        return saved;
    }
}