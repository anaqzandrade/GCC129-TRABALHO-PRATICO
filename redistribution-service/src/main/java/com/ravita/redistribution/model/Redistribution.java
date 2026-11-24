package com.ravita.redistribution.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "redistributions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Redistribution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // ids dos outros serviços são strings opacas (ex.: "offer-<uuid>", "org-<uuid>"), como no contrato
    @Column(nullable = false, length = 100)
    private String offerId;

    @Column(nullable = false, length = 100)
    private String institutionId;

    // reserva criada no offer-service (passo T2); usada na compensação e no cancelamento
    @Column(length = 100)
    private String reservationId;

    private UUID logisticsId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RedistributionStatus status;

    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
