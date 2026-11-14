package com.ravita.organization.repository;
import com.ravita.organization.domain.*; import org.springframework.data.domain.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface OrganizationRepository extends JpaRepository<Organization,String>{ boolean existsByDocument(String document); Page<Organization> findByType(OrganizationType type,Pageable pageable); Page<Organization> findByActive(boolean active,Pageable pageable); Page<Organization> findByTypeAndActive(OrganizationType type,boolean active,Pageable pageable); }
