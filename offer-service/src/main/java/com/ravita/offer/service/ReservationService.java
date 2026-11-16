package com.ravita.offer.service;

import com.ravita.offer.api.error.ApiException;
import com.ravita.offer.domain.Offer;
import com.ravita.offer.domain.Reservation;
import com.ravita.offer.repository.OfferRepository;
import com.ravita.offer.repository.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Passo T2 da SAGA de redistribuição (orquestrada pelo redistribution-service):
 * reservar a oferta (ReserveOffer) e a compensação, liberar a reserva (ReleaseOffer).
 * As duas operações são idempotentes, porque o orquestrador pode repetir comandos.
 */
@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final OfferRepository offerRepository;
    private final ReservationRepository reservationRepository;
    private final Clock clock;

    public ReservationService(OfferRepository offerRepository, ReservationRepository reservationRepository,
                              Clock clock) {
        this.offerRepository = offerRepository;
        this.reservationRepository = reservationRepository;
        this.clock = clock;
    }

    /**
     * ReserveOffer. Repetir o pedido com o mesmo redistributionId devolve a reserva já criada.
     * Se duas redistribuições disputam a mesma oferta, só uma vence (@Version na Offer);
     * a outra recebe 409 OFFER_NOT_AVAILABLE.
     */
    @Transactional
    public Reservation reserve(String offerId, String redistributionId) {
        Optional<Reservation> existing = reservationRepository.findByRedistributionId(redistributionId);
        if (existing.isPresent()) {
            return replay(existing.get(), offerId);
        }

        Offer offer = offerRepository.findById(offerId).orElseThrow(ApiException::notFound);
        Instant now = clock.instant();
        if (!offer.isReservable(now)) {
            throw offerNotAvailable();
        }

        offer.markReserved(now);
        Reservation reservation = reservationRepository.save(Reservation.create(offerId, redistributionId, now));
        try {
            // flush aqui para o conflito de versão aparecer dentro deste método e virar 409
            offerRepository.saveAndFlush(offer);
        } catch (ConcurrencyFailureException concurrentReservation) {
            log.info("Reserva concorrente perdeu a disputa pela oferta {}", offerId);
            throw offerNotAvailable();
        }
        log.info("Oferta {} reservada pela redistribuição {} (reserva {})",
                offerId, redistributionId, reservation.getId());
        return reservation;
    }

    @Transactional(readOnly = true)
    public Reservation get(String offerId, String reservationId) {
        return reservationRepository.findByIdAndOfferId(reservationId, offerId)
                .orElseThrow(ApiException::notFound);
    }

    /**
     * ReleaseOffer (compensação). Liberar uma reserva já liberada não faz nada e devolve 204.
     * Depois da coleta não há o que compensar: 409 OFFER_ALREADY_COLLECTED.
     */
    @Transactional
    public void release(String offerId, String reservationId) {
        Reservation reservation = reservationRepository.findByIdAndOfferId(reservationId, offerId)
                .orElseThrow(ApiException::notFound);
        if (!reservation.isActive()) {
            return;
        }
        Offer offer = offerRepository.findById(offerId).orElseThrow(ApiException::notFound);
        if (offer.isCollected()) {
            throw ApiException.conflict("OFFER_ALREADY_COLLECTED",
                    "A oferta já foi coletada; a reserva não pode ser liberada.");
        }
        Instant now = clock.instant();
        reservation.release(now);
        offer.markReleased(now);
        reservationRepository.save(reservation);
        offerRepository.saveAndFlush(offer);
        log.info("Reserva {} liberada; oferta {} agora está {}", reservationId, offerId, offer.getStatus());
    }

    private Reservation replay(Reservation existing, String offerId) {
        if (!existing.getOfferId().equals(offerId)) {
            throw ApiException.businessRule("INVALID_REDISTRIBUTION",
                    "Esta redistribuição já possui reserva de outra oferta.");
        }
        if (!existing.isActive()) {
            throw ApiException.conflict("STATE_CONFLICT",
                    "A reserva desta redistribuição já foi liberada.");
        }
        return existing;
    }

    private static ApiException offerNotAvailable() {
        return ApiException.conflict("OFFER_NOT_AVAILABLE", "A oferta não está disponível para reserva.");
    }
}
