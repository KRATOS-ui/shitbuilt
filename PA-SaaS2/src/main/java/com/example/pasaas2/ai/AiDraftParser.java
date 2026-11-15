package com.example.pasaas2.ai;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Interpreta el JSON (ya convertido a Map) que devuelve la IA y lo vuelve un borrador de evento (CU-03).
 * Es tolerante: nunca lanza por datos faltantes o mal formados, sino que los reporta en {@code missing}
 * para que el usuario los complete en la vista previa. Lógica pura, sin dependencias de Spring.
 */
public final class AiDraftParser {

    private static final String OFFSET_SUFFIX = "(Z|[+-]\\d{2}:?\\d{2})$";

    private AiDraftParser() {
    }

    public record Draft(String title, String description, LocalDateTime startTime, LocalDateTime endTime,
                        String frequency, Integer intervalCount, List<String> daysOfWeek, LocalDate untilDate,
                        List<String> missing) {

        public boolean complete() {
            return missing.isEmpty();
        }
    }

    public static Draft fromMap(Map<String, Object> data) {
        List<String> missing = new ArrayList<>();
        if (data == null) {
            return new Draft(null, null, null, null, null, null, null, null,
                    List.of("title", "startTime", "endTime"));
        }

        String title = text(data.get("title"));
        if (title == null) {
            missing.add("title");
        }
        String description = text(data.get("description"));

        LocalDateTime start = dateTime(data.get("startTime"));
        LocalDateTime end = dateTime(data.get("endTime"));
        if (start == null) {
            // Sin inicio, un fin suelto no es confiable: se pide completar ambos
            missing.add("startTime");
            missing.add("endTime");
            end = null;
        } else if (end == null || !end.isAfter(start)) {
            end = start.plusHours(1); // regla de negocio: si no hay duración se asume 1 hora
        }

        String frequency = null;
        Integer interval = null;
        List<String> days = null;
        LocalDate until = null;
        Object rawRecurrence = data.get("recurrence");
        if (rawRecurrence instanceof Map<?, ?> rec) {
            String freq = text(rec.get("frequency"));
            if (freq != null) {
                String upper = freq.toUpperCase(Locale.ROOT);
                if (upper.equals("DAILY") || upper.equals("WEEKLY") || upper.equals("MONTHLY")) {
                    frequency = upper;
                    interval = integer(rec.get("intervalCount"));
                    if (interval == null || interval < 1) {
                        interval = 1;
                    }
                    days = stringList(rec.get("daysOfWeek"));
                    until = date(rec.get("untilDate"));
                }
            }
        }

        return new Draft(title, description, start, end, frequency, interval, days, until, missing);
    }

    // ------------------------------------------------------------------ utilidades

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        if (s.isEmpty() || s.equalsIgnoreCase("null")) {
            return null;
        }
        return s;
    }

    private static Integer integer(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        String s = text(value);
        if (s == null) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDateTime dateTime(Object value) {
        String s = text(value);
        if (s == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(s.replaceFirst(OFFSET_SUFFIX, ""));
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDate date(Object value) {
        String s = text(value);
        if (s == null) {
            return null;
        }
        try {
            return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> raw) || raw.isEmpty()) {
            return null;
        }
        List<String> result = new ArrayList<>();
        for (Object o : raw) {
            String s = text(o);
            if (s != null) {
                result.add(s.toUpperCase(Locale.ROOT));
            }
        }
        return result.isEmpty() ? null : result;
    }
}
