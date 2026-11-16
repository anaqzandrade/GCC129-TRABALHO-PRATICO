package com.ravita.matching.repository;

import com.ravita.matching.model.AvailableOffer;
import com.ravita.matching.model.FoodCategory;
import com.ravita.matching.model.OfferStatus;
import com.ravita.matching.model.Unit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AvailableOfferRepository extends JpaRepository<AvailableOffer, String> {
    List<AvailableOffer> findByFoodCategoryAndUnitAndCityAndStatus(
        FoodCategory foodCategory, Unit unit, String city, OfferStatus status
    );
}