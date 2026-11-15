package com.example.pasaas2.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChecklistToggleRequest {
    @NotNull(message = "Debe indicar si la tarea está completada")
    private Boolean completed;
}
