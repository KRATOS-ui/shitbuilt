package com.example.pasaas2.repository;

import com.example.pasaas2.model.Membership;
import com.example.pasaas2.model.Organization;
import com.example.pasaas2.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    boolean existsByUserAndOrganization(User user, Organization organization);

    // Membresías vigentes: left_at es NULL. Una membresía cerrada conserva el historial.
    boolean existsByUserAndOrganizationAndLeftAtIsNull(User user, Organization organization);

    Optional<Membership> findByUserAndOrganizationAndLeftAtIsNull(User user, Organization organization);

    List<Membership> findByUserAndLeftAtIsNull(User user);

    List<Membership> findByOrganizationAndLeftAtIsNull(Organization organization);

    long countByOrganizationAndRoleAndLeftAtIsNull(Organization organization, String role);

    long countByOrganization(Organization organization);

    List<Membership> findByUser(User user);

    void deleteByUser(User user);
}
