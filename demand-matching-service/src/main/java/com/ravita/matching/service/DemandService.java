package com.ravita.matching.service;

import com.ravita.matching.dto.DemandInput;
import com.ravita.matching.dto.MatchDto;
import com.ravita.matching.dto.MatchesResponse;
import com.ravita.matching.model.AvailableOffer;
import com.ravita.matching.model.Demand;
import com.ravita.matching.model.OfferStatus;
import com.ravita.matching.repository.AvailableOfferRepository;
import com.ravita.matching.repository.DemandRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DemandService {

    @Autowired
    private DemandRepository demandRepository;

    @Autowired
    private AvailableOfferRepository offerRepository;

    public Demand createDemand(DemandInput input) {
        Demand demand = new Demand();
        demand.setId("dem-" + UUID.randomUUID().toString().substring(0, 8));
        demand.setInstitutionId(input.getInstitutionId());
        demand.setFoodCategory(input.getFoodCategory());
        demand.setDesiredQuantity(input.getDesiredQuantity());
        demand.setUnit(input.getUnit());
        demand.setCity(input.getCity());
        demand.setCreatedAt(OffsetDateTime.now());
        demand.setUpdatedAt(OffsetDateTime.now());
        
        return demandRepository.save(demand);
    }

    public Page<Demand> listDemands(int page, int size, String institutionId) {
        PageRequest pageRequest = PageRequest.of(page, size);
        if (institutionId != null && !institutionId.isEmpty()) {
            // Se preferir criar método com paginação no repository, pode ajustar.
            // Para simplificar, listamos filtrando ou via query method:
        }
        return demandRepository.findAll(pageRequest);
    }

    public Demand findById(String demandId) {
        return demandRepository.findById(demandId)
                .orElseThrow(() -> new RuntimeException("Demand not found with id: " + demandId));
    }

    public MatchesResponse findMatchesForDemand(String demandId) {
        Demand demand = findById(demandId);
        
        List<AvailableOffer> potentialOffers = offerRepository.findByFoodCategoryAndUnitAndCityAndStatus(
                demand.getFoodCategory(), 
                demand.getUnit(), 
                demand.getCity(), 
                OfferStatus.AVAILABLE
        );

        List<MatchDto> matches = potentialOffers.stream()
                .filter(offer -> offer.getAvailableUntil() == null || offer.getAvailableUntil().isAfter(OffsetDateTime.now()))
                .filter(offer -> offer.getQuantity().compareTo(demand.getDesiredQuantity()) >= 0)
                .map(offer -> new MatchDto(offer.getOfferId(), "HIGH"))
                .collect(Collectors.toList());

        return new MatchesResponse(demandId, matches, OffsetDateTime.now());
    }
}