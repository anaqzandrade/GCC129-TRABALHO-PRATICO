package com.ravita.offer.api;

import com.ravita.offer.api.dto.ReservationInput;
import com.ravita.offer.api.dto.ReservationResponse;
import com.ravita.offer.domain.Reservation;
import com.ravita.offer.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Reservas: usadas apenas pelo redistribution-service (orquestrador da SAGA).
 * POST = ReserveOffer (T2); DELETE = ReleaseOffer (compensação de T2).
 */
@RestController
@RequestMapping(OfferMapper.BASE_PATH + "/{offerId}/reservations")
public class ReservationController {

    private final ReservationService reservationService;
    private final OfferMapper mapper;

    public ReservationController(ReservationService reservationService, OfferMapper mapper) {
        this.reservationService = reservationService;
        this.mapper = mapper;
    }

    /** 201 também na repetição com o mesmo redistributionId: devolve a reserva original. */
    @PostMapping
    public ResponseEntity<ReservationResponse> reserve(@PathVariable String offerId,
                                                       @Valid @RequestBody ReservationInput input) {
        Reservation reservation = reservationService.reserve(offerId, input.redistributionId());
        return ResponseEntity.created(URI.create(OfferMapper.reservationPath(offerId, reservation.getId())))
                .body(mapper.toResponse(reservation));
    }

    @GetMapping("/{reservationId}")
    public ReservationResponse get(@PathVariable String offerId, @PathVariable String reservationId) {
        return mapper.toResponse(reservationService.get(offerId, reservationId));
    }

    /** 204 inclusive quando a liberação já tinha sido feita (compensação idempotente). */
    @DeleteMapping("/{reservationId}")
    public ResponseEntity<Void> release(@PathVariable String offerId, @PathVariable String reservationId) {
        reservationService.release(offerId, reservationId);
        return ResponseEntity.noContent().build();
    }
}
