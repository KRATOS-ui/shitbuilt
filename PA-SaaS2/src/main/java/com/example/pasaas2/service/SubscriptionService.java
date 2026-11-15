package com.example.pasaas2.service;

import com.example.pasaas2.dto.SubscriptionInfo;
import com.example.pasaas2.exception.UnauthorizedException;
import com.example.pasaas2.model.Membership;
import com.example.pasaas2.model.User;
import com.example.pasaas2.repository.MembershipRepository;
import com.example.pasaas2.repository.UserRepository;
import com.example.pasaas2.subscription.SubscriptionCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** Suscripciones individuales (RF-13). El pago es simulado: no hay pasarela de pagos en esta fase. */
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;

    @Transactional(readOnly = true)
    public SubscriptionInfo getSubscription(User principal) {
        return toInfo(reload(principal), LocalDate.now());
    }

    @Transactional
    public SubscriptionInfo purchaseIndividual(User principal, int months) {
        SubscriptionCalculator.validateMonths(months);
        User user = reload(principal);
        LocalDate today = LocalDate.now();

        boolean inOrganization = !membershipRepository.findByUserAndLeftAtIsNull(user).isEmpty();
        int pausedDays = user.getPausedDaysRemaining() == null ? 0 : user.getPausedDaysRemaining();

        if (inOrganization) {
            // Cubierto por la organización: lo comprado queda congelado y se activa al salir
            user.setSubscriptionStatus(SubscriptionCalculator.PAUSED);
            user.setPausedDaysRemaining(pausedDays + SubscriptionCalculator.daysInMonths(today, months));
            user.setSubscriptionEndDate(null);
        } else {
            LocalDate base = SubscriptionCalculator.ACTIVE.equals(user.getSubscriptionStatus())
                    ? user.getSubscriptionEndDate() : null;
            LocalDate end = SubscriptionCalculator.extend(base, today, months);
            if (SubscriptionCalculator.PAUSED.equals(user.getSubscriptionStatus()) && pausedDays > 0) {
                end = end.plusDays(pausedDays); // días congelados que no deben perderse
            }
            user.setSubscriptionStatus(SubscriptionCalculator.ACTIVE);
            user.setSubscriptionEndDate(end);
            user.setPausedDaysRemaining(0);
        }
        userRepository.save(user);
        return toInfo(user, today);
    }

    public SubscriptionInfo toInfo(User user, LocalDate today) {
        return SubscriptionInfo.builder()
                .status(user.getSubscriptionStatus())
                .endDate(user.getSubscriptionEndDate())
                .pausedDaysRemaining(user.getPausedDaysRemaining() == null ? 0 : user.getPausedDaysRemaining())
                .effectivePlan(effectivePlan(user, today))
                .build();
    }

    /** ORGANIZATION si alguna organización vigente del usuario tiene plan de pago activo; si no, INDIVIDUAL o FREE. */
    public String effectivePlan(User user, LocalDate today) {
        List<Membership> memberships = membershipRepository.findByUserAndLeftAtIsNull(user);
        boolean covered = memberships.stream().anyMatch(m ->
                SubscriptionCalculator.isOrganizationPlanActive(
                        m.getOrganization().getPlan(), m.getOrganization().getPlanEndDate(), today));
        return SubscriptionCalculator.effectivePlan(covered, user.getSubscriptionStatus(),
                user.getSubscriptionEndDate(), today);
    }

    /** Recarga el usuario desde la BD: el principal del token puede estar desactualizado o desvinculado. */
    public User reload(User principal) {
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new UnauthorizedException("Usuario no encontrado"));
    }
}
