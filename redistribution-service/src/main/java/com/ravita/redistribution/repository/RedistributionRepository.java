package com.ravita.redistribution.repository;

import com.ravita.redistribution.model.Redistribution;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface RedistributionRepository extends JpaRepository<Redistribution, UUID> {
}