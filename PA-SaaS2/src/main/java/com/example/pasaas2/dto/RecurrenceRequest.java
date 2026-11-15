package com.example.pasaas2.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/** Regla de recurrencia (RF-12). frequency: DAILY, WEEKLY o MONTHLY. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecurrenceRequest {
    private String frequency;
    private Integer intervalCount;
    // Solo WEEKLY: ["MONDAY","WEDNESDAY"]. Si es null se usa el día de la semana del inicio.
    private List<String> daysOfWeek;
    private LocalDate untilDate;
}
