package com.ravita.offer.readmodel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AvailableOfferReadRepository
        extends JpaRepository<AvailableOfferRead, String>, JpaSpecificationExecutor<AvailableOfferRead> {
}
