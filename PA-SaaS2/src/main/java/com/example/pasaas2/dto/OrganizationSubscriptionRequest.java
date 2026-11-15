package com.example.pasaas2.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OrganizationSubscriptionRequest {
    @NotBlank(message = "El plan es obligatorio (BUSINESS o FREE_TIER)")
    private String plan;

    // Obligatorio para BUSINESS (1 a 12)
    private Integer months;
}
