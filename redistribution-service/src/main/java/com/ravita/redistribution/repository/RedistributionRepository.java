package com.ravita.redistribution.repository;

import com.ravita.redistribution.model.Redistribution;
import com.ravita.redistribution.model.RedistributionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface RedistributionRepository extends JpaRepository<Redistribution, UUID> {

    Page<Redistribution> findByStatus(RedistributionStatus status, Pageable pageable);

    Page<Redistribution> findByInstitutionId(String institutionId, Pageable pageable);

    Page<Redistribution> findByStatusAndInstitutionId(RedistributionStatus status, String institutionId,
                                                      Pageable pageable);

    @Query("select r.status as status, count(r) as total from Redistribution r group by r.status")
    List<StatusCount> countByStatus();

    interface StatusCount {
        RedistributionStatus getStatus();

        long getTotal();
    }
}
