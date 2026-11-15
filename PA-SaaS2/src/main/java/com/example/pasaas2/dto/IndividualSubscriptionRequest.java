package com.example.pasaas2.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class IndividualSubscriptionRequest {
    @NotNull(message = "Debe indicar la duración en meses")
    @Min(value = 1, message = "La duración mínima es 1 mes")
    @Max(value = 12, message = "La duración máxima es 12 meses")
    private Integer months;
}
