package com.ravita.offer.api;

import com.ravita.offer.api.dto.AddressDto;
import com.ravita.offer.api.dto.LinkDto;
import com.ravita.offer.api.dto.OfferResponse;
import com.ravita.offer.api.dto.OfferSummaryResponse;
import com.ravita.offer.api.dto.ReservationResponse;
import com.ravita.offer.domain.Offer;
import com.ravita.offer.domain.Reservation;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converte entidades nas representações do contrato e monta os links HATEOAS.
 * Os links dependem do estado: "reserve" só aparece enquanto a oferta pode ser reservada.
 */
@Component
public class OfferMapper {

    public static final String BASE_PATH = "/api/v1/offers";

    private final ZoneId responseZone;
    private final Clock clock;

    public OfferMapper(ZoneId responseZone, Clock clock) {
        this.responseZone = responseZone;
        this.clock = clock;
    }

    public OfferResponse toResponse(Offer offer) {
        return new OfferResponse(
                offer.getId(),
                offer.getDonorId(),
                offer.getFoodCategory(),
                offer.getDescription(),
                plain(offer.getQuantity()),
                offer.getUnit(),
                time(offer.getAvailableFrom()),
                time(offer.getAvailableUntil()),
                AddressDto.from(offer.getPickupAddress()),
                offer.getStatus(),
                time(offer.getCreatedAt()),
                time(offer.getUpdatedAt()),
                links(offer));
    }

    public OfferSummaryResponse toSummary(Offer offer) {
        return new OfferSummaryResponse(
                offer.getId(),
                offer.getDonorId(),
                offer.getFoodCategory(),
                offer.getDescription(),
                plain(offer.getQuantity()),
                offer.getUnit(),
                time(offer.getAvailableFrom()),
                time(offer.getAvailableUntil()),
                offer.getStatus(),
                offer.getPickupAddress().getCity(),
                offer.getPickupAddress().getState(),
                time(offer.getCreatedAt()),
                links(offer));
    }

    public ReservationResponse toResponse(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getOfferId(),
                reservation.getRedistributionId(),
                reservation.getStatus(),
                time(reservation.getCreatedAt()),
                time(reservation.getReleasedAt()));
    }

    /** ETag forte do contrato, ex.: "v3". */
    public static String etag(Offer offer) {
        return "\"v" + offer.getVersion() + "\"";
    }

    public static String offerPath(String offerId) {
        return BASE_PATH + "/" + offerId;
    }

    public static String reservationPath(String offerId, String reservationId) {
        return offerPath(offerId) + "/reservations/" + reservationId;
    }

    private Map<String, LinkDto> links(Offer offer) {
        Map<String, LinkDto> links = new LinkedHashMap<>();
        links.put("self", LinkDto.get(offerPath(offer.getId())));
        // rota interna do organization-service; o Portal BFF omite este link
        links.put("donor", LinkDto.get("/api/v1/organizations/" + offer.getDonorId()));
        if (offer.isReservable(clock.instant())) {
            links.put("reserve", LinkDto.post(offerPath(offer.getId()) + "/reservations"));
        }
        // "edit" e "cancel" entram junto com PATCH e cancellation (Parte 4).
        return links;
    }

    private OffsetDateTime time(Instant instant) {
        return instant == null ? null : instant.atZone(responseZone).toOffsetDateTime();
    }

    /** 30.000 (como vem do NUMERIC(12,3)) vira 30 na resposta, e 2.500 vira 2.5 (nunca 3E+1). */
    private static BigDecimal plain(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
