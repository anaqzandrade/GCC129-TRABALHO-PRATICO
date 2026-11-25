package com.ravita.logistics.repository;

import com.ravita.logistics.model.LogisticsOperation;
import com.ravita.logistics.model.LogisticsStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LogisticsRepository extends JpaRepository<LogisticsOperation, UUID> {

    Page<LogisticsOperation> findByStatus(LogisticsStatus status, Pageable pageable);

    Page<LogisticsOperation> findByRedistributionId(UUID redistributionId, Pageable pageable);

    Page<LogisticsOperation> findByStatusAndRedistributionId(LogisticsStatus status, UUID redistributionId,
                                                             Pageable pageable);
}
