package com.example.pasaas2.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChecklistCreateRequest {
    @NotBlank(message = "La descripción de la tarea es obligatoria")
    private String description;
}
