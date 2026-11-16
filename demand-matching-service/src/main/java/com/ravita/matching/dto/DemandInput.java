package com.ravita.matching.dto;

import com.ravita.matching.model.FoodCategory;
import com.ravita.matching.model.Unit;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class DemandInput {
    private String institutionId;
    private FoodCategory foodCategory;
    private BigDecimal desiredQuantity;
    private Unit unit;
    private String city;
}