package main.java.com.ravita.matching.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "demands")
@Data
public class Demand {
    @Id
    private String id;
    
    @Column(nullable = false)
    private String institutionId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FoodCategory foodCategory;
    
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal desiredQuantity;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Unit unit;
    
    @Column(nullable = false)
    private String city;
    
    @Column(nullable = false)
    private OffsetDateTime createdAt;
    
    @Column(nullable = false)
    private OffsetDateTime updatedAt;
}