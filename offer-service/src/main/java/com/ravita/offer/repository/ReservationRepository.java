package com.ravita.offer.repository;

import com.ravita.offer.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, String> {

    Optional<Reservation> findByRedistributionId(String redistributionId);

    Optional<Reservation> findByIdAndOfferId(String id, String offerId);
}
