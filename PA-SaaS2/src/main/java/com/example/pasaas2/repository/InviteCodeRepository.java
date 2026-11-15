package com.example.pasaas2.repository;

import com.example.pasaas2.model.InviteCode;
import com.example.pasaas2.model.Organization;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InviteCodeRepository extends JpaRepository<InviteCode, UUID> {

    Optional<InviteCode> findByCode(String code);

    boolean existsByCode(String code);

    List<InviteCode> findByOrganizationOrderByCreatedAtDesc(Organization organization);

    // Bloqueo de escritura: evita que dos usuarios consuman a la vez el último uso de un código
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM InviteCode i WHERE i.code = :code")
    Optional<InviteCode> findByCodeForUpdate(@Param("code") String code);

    void deleteByOrganization(Organization organization);
}
