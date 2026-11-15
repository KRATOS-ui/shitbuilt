package com.example.pasaas2.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Respuesta única para eventos. Los campos occurrenceStart/occurrenceEnd/recurring/eventId solo se llenan
 * en /events/occurrences y assignees solo en el calendario general de la organización.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EventResponse {
    private UUID id;
    private String title;
    private String description;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Boolean hasConflict;
    private String recurrenceFrequency;
    private Integer intervalCount;
    private List<String> daysOfWeek;
    private LocalDate untilDate;
    private UUID organizationId;
    private String organizationName;
    private String createdByName;
    private Boolean owner;

    // Solo /events/occurrences
    private UUID eventId;
    private LocalDateTime occurrenceStart;
    private LocalDateTime occurrenceEnd;
    private Boolean recurring;

    // Solo /organizations/{id}/events
    private List<AssigneeInfo> assignees;
}
