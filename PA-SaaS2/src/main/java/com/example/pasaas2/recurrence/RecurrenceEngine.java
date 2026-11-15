package com.example.pasaas2.recurrence;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Motor de recurrencia (RF-12). Es lógica pura, sin dependencias de Spring, para poder probarla
 * de forma aislada. Expande un evento base en sus ocurrencias dentro de una ventana de tiempo.
 *
 * Reglas:
 *  - DAILY: cada N días desde la fecha de inicio.
 *  - WEEKLY: cada N semanas (semanas que empiezan en lunes), en los días indicados; si no se
 *    indican, usa el día de la semana del inicio.
 *  - MONTHLY: cada N meses el mismo día del mes; si el mes no tiene ese día (ej. 31) usa el último.
 *  - untilDate es inclusiva y se compara contra la fecha de inicio de cada ocurrencia.
 *  - Nunca se generan ocurrencias anteriores al inicio original; la duración se conserva.
 */
public final class RecurrenceEngine {

    public static final int MAX_OCCURRENCES = 2000;

    private RecurrenceEngine() {
    }

    public record Spec(String frequency, int intervalCount, Set<DayOfWeek> daysOfWeek, LocalDate untilDate) {

        /** Construye y valida una regla a partir de los datos crudos de la API. */
        public static Spec of(String frequency, Integer intervalCount, Collection<String> days, LocalDate until) {
            if (frequency == null || frequency.isBlank()) {
                throw new IllegalArgumentException("La frecuencia de la recurrencia es obligatoria");
            }
            String freq = frequency.trim().toUpperCase(Locale.ROOT);
            if (!freq.equals("DAILY") && !freq.equals("WEEKLY") && !freq.equals("MONTHLY")) {
                throw new IllegalArgumentException("Frecuencia no válida: use DAILY, WEEKLY o MONTHLY");
            }
            int interval = intervalCount == null ? 1 : intervalCount;
            if (interval < 1) {
                throw new IllegalArgumentException("El intervalo de repetición debe ser al menos 1");
            }
            Set<DayOfWeek> parsedDays = EnumSet.noneOf(DayOfWeek.class);
            if (freq.equals("WEEKLY") && days != null) {
                for (String d : days) {
                    if (d == null || d.isBlank()) {
                        continue;
                    }
                    try {
                        parsedDays.add(DayOfWeek.valueOf(d.trim().toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("Día de la semana no válido: " + d);
                    }
                }
            }
            return new Spec(freq, interval, parsedDays, until);
        }
    }

    public record Occurrence(LocalDateTime start, LocalDateTime end) {
    }

    /** "MONDAY,WEDNESDAY" -> conjunto ordenado de días. */
    public static Set<DayOfWeek> parseDays(String csv) {
        Set<DayOfWeek> result = EnumSet.noneOf(DayOfWeek.class);
        if (csv == null || csv.isBlank()) {
            return result;
        }
        for (String part : csv.split(",")) {
            if (!part.isBlank()) {
                result.add(DayOfWeek.valueOf(part.trim().toUpperCase(Locale.ROOT)));
            }
        }
        return result;
    }

    /** Conjunto de días -> "MONDAY,WEDNESDAY" (orden lunes a domingo); null si está vacío. */
    public static String joinDays(Collection<DayOfWeek> days) {
        if (days == null || days.isEmpty()) {
            return null;
        }
        List<String> names = new ArrayList<>();
        new TreeSet<>(days).forEach(d -> names.add(d.name()));
        return String.join(",", names);
    }

    /**
     * Devuelve las ocurrencias del evento que se solapan con la ventana [from, to).
     * Si spec es null el evento es único y se devuelve como máximo una ocurrencia.
     */
    public static List<Occurrence> expand(LocalDateTime start, LocalDateTime end, Spec spec,
                                          LocalDateTime from, LocalDateTime to) {
        List<Occurrence> result = new ArrayList<>();
        if (start == null || end == null || from == null || to == null || !from.isBefore(to)) {
            return result;
        }
        Duration duration = Duration.between(start, end);
        if (duration.isNegative()) {
            return result;
        }

        if (spec == null) {
            addIfOverlaps(result, start, start.plus(duration), from, to);
            return result;
        }

        LocalDate startDate = start.toLocalDate();
        // Fecha mínima de inicio de ocurrencia que aún podría solaparse con la ventana.
        long durationDays = duration.toDays() + 1;
        LocalDate earliest = from.toLocalDate().minusDays(durationDays);
        LocalDate latest = to.toLocalDate();
        LocalDate until = spec.untilDate();
        if (until != null && until.isBefore(latest)) {
            latest = until;
        }
        int step = Math.max(1, spec.intervalCount());

        switch (spec.frequency()) {
            case "DAILY" -> {
                long k = 0;
                if (earliest.isAfter(startDate)) {
                    long gap = ChronoUnit.DAYS.between(startDate, earliest);
                    k = (gap / step) * step; // retrocede a un múltiplo del intervalo, sin pasarse
                }
                for (LocalDate d = startDate.plusDays(k); !d.isAfter(latest) && result.size() < MAX_OCCURRENCES;
                     d = d.plusDays(step)) {
                    addIfOverlaps(result, LocalDateTime.of(d, start.toLocalTime()), duration, from, to);
                }
            }
            case "WEEKLY" -> {
                Set<DayOfWeek> days = spec.daysOfWeek() == null || spec.daysOfWeek().isEmpty()
                        ? EnumSet.of(start.getDayOfWeek())
                        : spec.daysOfWeek();
                List<DayOfWeek> ordered = new ArrayList<>(days);
                ordered.sort(Comparator.naturalOrder());
                LocalDate firstWeek = startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                long w = 0;
                if (earliest.isAfter(firstWeek)) {
                    long weeksGap = ChronoUnit.WEEKS.between(firstWeek, earliest);
                    w = (weeksGap / step) * step;
                }
                for (; result.size() < MAX_OCCURRENCES; w += step) {
                    LocalDate weekStart = firstWeek.plusWeeks(w);
                    if (weekStart.isAfter(latest)) {
                        break;
                    }
                    for (DayOfWeek dow : ordered) {
                        LocalDate d = weekStart.plusDays(dow.getValue() - 1L);
                        if (d.isBefore(startDate) || d.isAfter(latest)) {
                            continue;
                        }
                        addIfOverlaps(result, LocalDateTime.of(d, start.toLocalTime()), duration, from, to);
                        if (result.size() >= MAX_OCCURRENCES) {
                            break;
                        }
                    }
                }
            }
            case "MONTHLY" -> {
                YearMonth firstMonth = YearMonth.from(startDate);
                long m = 0;
                if (earliest.isAfter(startDate)) {
                    long monthsGap = ChronoUnit.MONTHS.between(firstMonth, YearMonth.from(earliest));
                    m = (monthsGap / step) * step;
                }
                for (; result.size() < MAX_OCCURRENCES; m += step) {
                    YearMonth ym = firstMonth.plusMonths(m);
                    if (ym.atDay(1).isAfter(latest)) {
                        break;
                    }
                    int day = Math.min(startDate.getDayOfMonth(), ym.lengthOfMonth());
                    LocalDate d = ym.atDay(day);
                    if (d.isBefore(startDate) || d.isAfter(latest)) {
                        continue;
                    }
                    addIfOverlaps(result, LocalDateTime.of(d, start.toLocalTime()), duration, from, to);
                }
            }
            default -> throw new IllegalArgumentException("Frecuencia no soportada: " + spec.frequency());
        }
        return result;
    }

    private static void addIfOverlaps(List<Occurrence> out, LocalDateTime occStart, Duration duration,
                                      LocalDateTime from, LocalDateTime to) {
        addIfOverlaps(out, occStart, occStart.plus(duration), from, to);
    }

    private static void addIfOverlaps(List<Occurrence> out, LocalDateTime occStart, LocalDateTime occEnd,
                                      LocalDateTime from, LocalDateTime to) {
        if (!occStart.isBefore(to)) {
            return;
        }
        boolean zeroLength = occStart.equals(occEnd);
        boolean overlaps = zeroLength ? !occStart.isBefore(from) : occEnd.isAfter(from);
        if (overlaps) {
            out.add(new Occurrence(occStart, occEnd));
        }
    }
}
