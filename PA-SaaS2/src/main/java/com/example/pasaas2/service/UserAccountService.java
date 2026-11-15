package com.example.pasaas2.service;

import com.example.pasaas2.dto.MembershipInfo;
import com.example.pasaas2.dto.UserProfileResponse;
import com.example.pasaas2.exception.BusinessException;
import com.example.pasaas2.mapper.ApiMapper;
import com.example.pasaas2.model.Event;
import com.example.pasaas2.model.Membership;
import com.example.pasaas2.model.Organization;
import com.example.pasaas2.model.User;
import com.example.pasaas2.repository.EventAssignmentRepository;
import com.example.pasaas2.repository.EventRepository;
import com.example.pasaas2.repository.InviteCodeRepository;
import com.example.pasaas2.repository.MembershipRepository;
import com.example.pasaas2.repository.OrganizationRepository;
import com.example.pasaas2.repository.TimelineEntryRepository;
import com.example.pasaas2.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Derechos del titular de los datos (Ley 1581 de 2012): conocer, actualizar y suprimir. */
@Service
@RequiredArgsConstructor
public class UserAccountService {

    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final OrganizationRepository organizationRepository;
    private final InviteCodeRepository inviteCodeRepository;
    private final EventRepository eventRepository;
    private final EventAssignmentRepository eventAssignmentRepository;
    private final TimelineEntryRepository timelineEntryRepository;
    private final SubscriptionService subscriptionService;
    private final EventService eventService;

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(User principal) {
        return buildProfile(subscriptionService.reload(principal));
    }

    @Transactional
    public UserProfileResponse updateName(User principal, String name) {
        User user = subscriptionService.reload(principal);
        user.setName(name.trim());
        userRepository.save(user);
        return buildProfile(user);
    }

    /**
     * Elimina la cuenta y los datos asociados. Un administrador con empleados activos debe retirarlos
     * antes (si no, la organización quedaría sin administrador).
     */
    @Transactional
    public void deleteAccount(User principal) {
        User user = subscriptionService.reload(principal);
        List<Membership> memberships = membershipRepository.findByUser(user);

        for (Membership m : memberships) {
            if (m.getLeftAt() == null && OrganizationService.ROLE_ADMIN.equals(m.getRole())) {
                long others = membershipRepository.findByOrganizationAndLeftAtIsNull(m.getOrganization()).stream()
                        .filter(x -> !x.getUser().getId().equals(user.getId()))
                        .count();
                if (others > 0) {
                    throw new BusinessException("Eres administrador de una organización con empleados activos. "
                            + "Retira a los empleados antes de eliminar tu cuenta.");
                }
            }
        }

        // 1. Eventos creados por el usuario (con sus asignaciones, regla, checklist y contexto)
        for (Event event : eventRepository.findByCreatedBy(user)) {
            eventService.deleteEventAsCreator(event, user);
        }
        eventRepository.flush();

        // 2. Lo que el usuario dejó en eventos de otras personas
        eventAssignmentRepository.deleteByUser(user);
        timelineEntryRepository.deleteByAuthor(user);

        // 3. Membresías; las organizaciones que queden sin ningún registro se eliminan
        Map<UUID, Organization> organizations = new LinkedHashMap<>();
        for (Membership m : memberships) {
            organizations.put(m.getOrganization().getId(), m.getOrganization());
        }
        membershipRepository.deleteByUser(user);
        membershipRepository.flush();
        for (Organization org : organizations.values()) {
            if (membershipRepository.countByOrganization(org) == 0) {
                inviteCodeRepository.deleteByOrganization(org);
                organizationRepository.delete(org);
            }
        }

        // 4. La cuenta
        userRepository.delete(user);
    }

    private UserProfileResponse buildProfile(User user) {
        LocalDate today = LocalDate.now();
        List<MembershipInfo> memberships = membershipRepository.findByUserAndLeftAtIsNull(user).stream()
                .map(ApiMapper::toMembershipInfo)
                .toList();
        return UserProfileResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .subscriptionStatus(user.getSubscriptionStatus())
                .subscriptionEndDate(user.getSubscriptionEndDate())
                .pausedDaysRemaining(user.getPausedDaysRemaining() == null ? 0 : user.getPausedDaysRemaining())
                .effectivePlan(subscriptionService.effectivePlan(user, today))
                .memberships(memberships)
                .build();
    }
}
