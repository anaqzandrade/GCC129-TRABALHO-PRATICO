package com.ravita.logistics.repository;

import com.ravita.logistics.model.LogisticsOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface LogisticsRepository extends JpaRepository<LogisticsOperation, UUID> {
}