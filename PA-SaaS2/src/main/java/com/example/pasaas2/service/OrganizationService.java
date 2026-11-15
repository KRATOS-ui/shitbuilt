package com.example.pasaas2.service;

import com.example.pasaas2.dto.InviteCodeRequest;
import com.example.pasaas2.dto.InviteCodeResponse;
import com.example.pasaas2.dto.JoinOrganizationRequest;
import com.example.pasaas2.dto.MemberResponse;
import com.example.pasaas2.dto.MembershipInfo;
import com.example.pasaas2.dto.MembershipResponse;
import com.example.pasaas2.dto.OrganizationRequest;
import com.example.pasaas2.dto.OrganizationResponse;
import com.example.pasaas2.dto.OrganizationSubscriptionRequest;
import com.example.pasaas2.exception.BusinessException;
import com.example.pasaas2.exception.ForbiddenException;
import com.example.pasaas2.exception.ResourceNotFoundException;
import com.example.pasaas2.mapper.ApiMapper;
import com.example.pasaas2.model.InviteCode;
import com.example.pasaas2.model.Membership;
import com.example.pasaas2.model.Organization;
import com.example.pasaas2.model.User;
import com.example.pasaas2.repository.InviteCodeRepository;
import com.example.pasaas2.repository.MembershipRepository;
import com.example.pasaas2.repository.OrganizationRepository;
import com.example.pasaas2.repository.UserRepository;
import com.example.pasaas2.subscription.SubscriptionCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_EMPLOYEE = "EMPLOYEE";
    public static final String PLAN_FREE = "FREE_TIER";
    public static final String PLAN_BUSINESS = "BUSINESS";

    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final InviteCodeRepository inviteCodeRepository;
    private final UserRepository userRepository;

    // ------------------------------------------------------------------ creación

    @Transactional
    public OrganizationResponse createOrganization(OrganizationRequest request, User principal) {
        User currentUser = reload(principal);

        Organization org = Organization.builder()
                .name(request.getName().trim())
                .plan(PLAN_FREE)
                .build();
        org = organizationRepository.save(org);

        // El creador queda como administrador (RF-09)
        Membership membership = Membership.builder()
                .user(currentUser)
                .organization(org)
                .role(ROLE_ADMIN)
                .joinedAt(LocalDateTime.now())
                .build();
        membershipRepository.save(membership);

        // Si tenía una suscripción individual activa, la organización lo cubre: se congela (igual que al unirse)
        freezeIndividualSubscription(currentUser);

        return ApiMapper.toOrganizationResponse(org);
    }

    // ------------------------------------------------------------------ roles y permisos

    /** Devuelve la membresía vigente del usuario en la organización o lanza 403 (aislamiento, RNF-02). */
    @Transactional(readOnly = true)
    public Membership requireActiveMember(User principal, UUID organizationId) {
        Organization org = findOrganization(organizationId);
        return membershipRepository.findByUserAndOrganizationAndLeftAtIsNull(principal, org)
                .orElseThrow(() -> new ForbiddenException("No perteneces a esta organización"));
    }

    /** Devuelve la membresía solo si el usuario es ADMIN vigente de la organización; si no, 403 (RF-06). */
    @Transactional(readOnly = true)
    public Membership requireAdmin(User principal, UUID organizationId) {
        Membership membership = requireActiveMember(principal, organizationId);
        if (!ROLE_ADMIN.equals(membership.getRole())) {
            throw new ForbiddenException("Esta acción es solo para administradores de la organización");
        }
        return membership;
    }

    public Organization findOrganization(UUID organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organización no encontrada"));
    }

    // ------------------------------------------------------------------ códigos de invitación

    @Transactional
    public InviteCodeResponse generateInviteCode(UUID organizationId, InviteCodeRequest request, User principal) {
        Membership admin = requireAdmin(principal, organizationId);
        Organization org = admin.getOrganization();

        String uniqueCode;
        do {
            uniqueCode = "ORG-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        } while (inviteCodeRepository.existsByCode(uniqueCode));

        InviteCode inviteCode = InviteCode.builder()
                .organization(org)
                .code(uniqueCode)
                .maxUses(request.getMaxUses())
                .usesCount(0)
                .expiresAt(LocalDateTime.now().plusDays(7)) // Expira en 7 días por defecto
                .build();

        return ApiMapper.toInviteCodeResponse(inviteCodeRepository.save(inviteCode));
    }

    @Transactional(readOnly = true)
    public List<InviteCodeResponse> listInviteCodes(UUID organizationId, User principal) {
        Membership admin = requireAdmin(principal, organizationId);
        return inviteCodeRepository.findByOrganizationOrderByCreatedAtDesc(admin.getOrganization()).stream()
                .map(ApiMapper::toInviteCodeResponse)
                .toList();
    }

    // ------------------------------------------------------------------ unirse / salir (CU-01)

    @Transactional
    public MembershipResponse joinOrganization(JoinOrganizationRequest request, User principal) {
        User currentUser = reload(principal);
        String code = request.getCode().trim().toUpperCase(Locale.ROOT);

        // Flujo alternativo: código inexistente, expirado o sin usos -> error y no se crea la membresía
        InviteCode inviteCode = inviteCodeRepository.findByCodeForUpdate(code)
                .orElseThrow(() -> new BusinessException("Código de invitación inválido o no existe"));

        int used = inviteCode.getUsesCount() == null ? 0 : inviteCode.getUsesCount();
        int max = inviteCode.getMaxUses() == null ? 0 : inviteCode.getMaxUses();
        if (used >= max) {
            throw new BusinessException("El código de invitación ha alcanzado su límite de usos");
        }
        if (inviteCode.getExpiresAt() != null && inviteCode.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("El código de invitación ha expirado");
        }

        Organization org = inviteCode.getOrganization();
        if (membershipRepository.existsByUserAndOrganizationAndLeftAtIsNull(currentUser, org)) {
            throw new BusinessException("Ya perteneces a esta organización");
        }

        // 1. Consumir un uso del código
        inviteCode.setUsesCount(used + 1);
        inviteCodeRepository.save(inviteCode);

        // 2. Regla de negocio: la suscripción individual activa se pausa conservando los días restantes
        freezeIndividualSubscription(currentUser);

        // 3. Crear membresía como empleado
        Membership membership = Membership.builder()
                .user(currentUser)
                .organization(org)
                .role(ROLE_EMPLOYEE)
                .joinedAt(LocalDateTime.now())
                .build();
        membership = membershipRepository.save(membership);

        return MembershipResponse.builder()
                .id(membership.getId())
                .role(membership.getRole())
                .joinedAt(membership.getJoinedAt())
                .organizationName(org.getName())
                .userEmail(currentUser.getEmail())
                .build();
    }

    @Transactional
    public void leaveOrganization(UUID organizationId, User principal) {
        User currentUser = reload(principal);
        Membership membership = requireActiveMember(currentUser, organizationId);

        if (ROLE_ADMIN.equals(membership.getRole())) {
            long admins = membershipRepository.countByOrganizationAndRoleAndLeftAtIsNull(
                    membership.getOrganization(), ROLE_ADMIN);
            if (admins <= 1) {
                throw new BusinessException(
                        "Eres el único administrador de la organización. Retira a los empleados antes de salir.");
            }
        }
        closeMembership(membership);
    }

    /** Un administrador retira a un empleado de su organización. */
    @Transactional
    public void removeMember(UUID organizationId, UUID memberUserId, User principal) {
        Membership admin = requireAdmin(principal, organizationId);
        User target = userRepository.findById(memberUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        Membership membership = membershipRepository
                .findByUserAndOrganizationAndLeftAtIsNull(target, admin.getOrganization())
                .orElseThrow(() -> new ResourceNotFoundException("El usuario no es miembro activo de la organización"));
        if (ROLE_ADMIN.equals(membership.getRole())) {
            throw new BusinessException("No se puede retirar a un administrador");
        }
        closeMembership(membership);
    }

    /**
     * Cierra la membresía (left_at) y, si el usuario ya no está cubierto por ninguna otra organización,
     * reanuda su suscripción individual pausada: end_date = hoy + paused_days_remaining (CU-01, flujo de salida).
     */
    private void closeMembership(Membership membership) {
        User user = membership.getUser();
        membership.setLeftAt(LocalDateTime.now());
        membershipRepository.save(membership);

        boolean stillCovered = membershipRepository.findByUserAndLeftAtIsNull(user).stream()
                .anyMatch(m -> !m.getId().equals(membership.getId()));
        if (!stillCovered && SubscriptionCalculator.PAUSED.equals(user.getSubscriptionStatus())) {
            int paused = user.getPausedDaysRemaining() == null ? 0 : user.getPausedDaysRemaining();
            if (paused > 0) {
                user.setSubscriptionStatus(SubscriptionCalculator.ACTIVE);
                user.setSubscriptionEndDate(SubscriptionCalculator.resumeEndDate(paused, LocalDate.now()));
            } else {
                user.setSubscriptionStatus(SubscriptionCalculator.INACTIVE);
                user.setSubscriptionEndDate(null);
            }
            user.setPausedDaysRemaining(0);
            userRepository.save(user);
        }
    }

    /** Congela una suscripción individual ACTIVE: guarda los días restantes y pasa a PAUSED. */
    private void freezeIndividualSubscription(User user) {
        if (SubscriptionCalculator.ACTIVE.equals(user.getSubscriptionStatus())) {
            int remaining = SubscriptionCalculator.daysToFreeze(user.getSubscriptionEndDate(), LocalDate.now());
            if (remaining > 0) {
                user.setSubscriptionStatus(SubscriptionCalculator.PAUSED);
                user.setPausedDaysRemaining(remaining);
                user.setSubscriptionEndDate(null);
            } else {
                user.setSubscriptionStatus(SubscriptionCalculator.INACTIVE);
                user.setSubscriptionEndDate(null);
                user.setPausedDaysRemaining(0);
            }
            userRepository.save(user);
        }
    }

    // ------------------------------------------------------------------ consultas

    @Transactional(readOnly = true)
    public List<MembershipInfo> listMyMemberships(User principal) {
        return membershipRepository.findByUserAndLeftAtIsNull(principal).stream()
                .map(ApiMapper::toMembershipInfo)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers(UUID organizationId, User principal) {
        Membership admin = requireAdmin(principal, organizationId);
        return membershipRepository.findByOrganizationAndLeftAtIsNull(admin.getOrganization()).stream()
                .map(ApiMapper::toMemberResponse)
                .toList();
    }

    // ------------------------------------------------------------------ suscripción de la organización

    /** Contratar o cancelar el plan de la organización (pago simulado). Solo ADMIN. */
    @Transactional
    public OrganizationResponse updateSubscription(UUID organizationId, OrganizationSubscriptionRequest request,
                                                   User principal) {
        Membership admin = requireAdmin(principal, organizationId);
        Organization org = admin.getOrganization();
        String plan = request.getPlan().trim().toUpperCase(Locale.ROOT);

        if (PLAN_BUSINESS.equals(plan)) {
            if (request.getMonths() == null) {
                throw new BusinessException("Debes indicar la duración en meses (1 a 12)");
            }
            org.setPlan(PLAN_BUSINESS);
            org.setPlanEndDate(SubscriptionCalculator.extend(org.getPlanEndDate(), LocalDate.now(), request.getMonths()));
        } else if (PLAN_FREE.equals(plan)) {
            org.setPlan(PLAN_FREE);
            org.setPlanEndDate(null);
        } else {
            throw new BusinessException("Plan no válido: use BUSINESS o FREE_TIER");
        }
        return ApiMapper.toOrganizationResponse(organizationRepository.save(org));
    }

    private User reload(User principal) {
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }
}
