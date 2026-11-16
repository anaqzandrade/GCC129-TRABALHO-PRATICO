package com.ravita.organization.domain;

import jakarta.persistence.Embeddable;
import java.math.BigDecimal;

@Embeddable
public class ReceivingCapacity {
    private String foodCategory;
    private BigDecimal maxQuantity;
    private String unit;
    protected ReceivingCapacity() {}
    public ReceivingCapacity(String foodCategory, BigDecimal maxQuantity, String unit) { this.foodCategory=foodCategory; this.maxQuantity=maxQuantity; this.unit=unit; }
    public String getFoodCategory(){return foodCategory;} public BigDecimal getMaxQuantity(){return maxQuantity;} public String getUnit(){return unit;}
}
