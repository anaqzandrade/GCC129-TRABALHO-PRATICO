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

    @Column(nullable = false)
    private UUID offerId;

    @Column(nullable = false)
    private UUID institutionId;

    private UUID logisticsId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RedistributionStatus status;

    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}