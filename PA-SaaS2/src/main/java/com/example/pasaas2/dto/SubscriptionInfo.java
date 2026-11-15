package com.example.pasaas2.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SubscriptionInfo {
    private String status;
    private LocalDate endDate;
    private Integer pausedDaysRemaining;
    private String effectivePlan;
}
