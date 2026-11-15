package com.example.pasaas2.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** Borrador de evento interpretado por la IA (CU-03). No se guarda nada hasta que el usuario confirma. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EventPreview {
    private String title;
    private String description;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private RecurrenceRequest recurrence;
    private List<String> missingFields;
    private Boolean complete;
}
