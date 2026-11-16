package main.java.com.ravita.matching.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "available_offers")
@Data
public class AvailableOffer {
    @Id
    private String offerId;
    
    private String donorId;
    
    @Enumerated(EnumType.STRING)
    private FoodCategory foodCategory;
    
    @Column(precision = 12, scale = 3)
    private BigDecimal quantity;
    
    @Enumerated(EnumType.STRING)
    private Unit unit;
    
    private String city;
    
    @Enumerated(EnumType.STRING)
    private OfferStatus status;
    
    private OffsetDateTime availableUntil;
}

enum OfferStatus {
    AVAILABLE, RESERVED, COLLECTED, COMPLETED, CANCELLED, EXPIRED
}