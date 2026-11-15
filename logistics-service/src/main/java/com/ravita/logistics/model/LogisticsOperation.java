package com.ravita.logistics.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "logistics_operations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LogisticsOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID redistributionId;

    @Column(nullable = false)
    private UUID offerId;

    private String pickupAddress;
    private String deliveryAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LogisticsStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime collectedAt;
    private LocalDateTime deliveredAt;
}