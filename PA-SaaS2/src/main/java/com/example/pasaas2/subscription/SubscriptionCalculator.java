package com.example.pasaas2.subscription;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Reglas de negocio de suscripciones (RF-13, CU-01). Lógica pura y sin dependencias de Spring.
 * Regla central: la suscripción individual nunca pierde días por el tiempo en que el usuario
 * estuvo cubierto por una organización; se "congela" y se retoma al salir.
 */
public final class SubscriptionCalculator {

    public static final String INACTIVE = "INACTIVE";
    public static final String ACTIVE = "ACTIVE";
    public static final String PAUSED = "PAUSED";

    public static final String PLAN_FREE = "FREE";
    public static final String PLAN_INDIVIDUAL = "INDIVIDUAL";
    public static final String PLAN_ORGANIZATION = "ORGANIZATION";

    private SubscriptionCalculator() {
    }

    /** Días que quedan de una suscripción activa: end_date - hoy (nunca negativo). */
    public static int daysToFreeze(LocalDate endDate, LocalDate today) {
        if (endDate == null) {
            return 0;
        }
        return (int) Math.max(0, ChronoUnit.DAYS.between(today, endDate));
    }

    /** Al salir de la organización: nueva fecha de fin = hoy + días congelados. */
    public static LocalDate resumeEndDate(Integer pausedDays, LocalDate today) {
        int days = pausedDays == null ? 0 : Math.max(0, pausedDays);
        return today.plusDays(days);
    }

    /** Extiende una suscripción: parte de la fecha de fin vigente, o de hoy si ya venció o no existe. */
    public static LocalDate extend(LocalDate currentEnd, LocalDate today, int months) {
        validateMonths(months);
        LocalDate base = (currentEnd != null && currentEnd.isAfter(today)) ? currentEnd : today;
        return base.plusMonths(months);
    }

    /** Días que aporta comprar `months` meses, cuando la suscripción está congelada. */
    public static int daysInMonths(LocalDate today, int months) {
        validateMonths(months);
        return (int) ChronoUnit.DAYS.between(today, today.plusMonths(months));
    }

    /** ¿La organización tiene un plan de pago vigente? */
    public static boolean isOrganizationPlanActive(String plan, LocalDate planEndDate, LocalDate today) {
        return "BUSINESS".equals(plan) && planEndDate != null && !planEndDate.isBefore(today);
    }

    /** Plan efectivo del usuario: ORGANIZATION > INDIVIDUAL > FREE. */
    public static String effectivePlan(boolean coveredByActiveOrganizationPlan, String status,
                                       LocalDate endDate, LocalDate today) {
        if (coveredByActiveOrganizationPlan) {
            return PLAN_ORGANIZATION;
        }
        if (ACTIVE.equals(status) && endDate != null && !endDate.isBefore(today)) {
            return PLAN_INDIVIDUAL;
        }
        return PLAN_FREE;
    }

    public static void validateMonths(int months) {
        if (months < 1 || months > 12) {
            throw new IllegalArgumentException("La duración debe estar entre 1 y 12 meses");
        }
    }
}
