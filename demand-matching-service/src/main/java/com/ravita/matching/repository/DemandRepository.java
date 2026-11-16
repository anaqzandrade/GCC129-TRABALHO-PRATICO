package com.ravita.matching.repository;

import com.ravita.matching.model.Demand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DemandRepository extends JpaRepository<Demand, String> {
    List<Demand> findByInstitutionId(String institutionId);
}