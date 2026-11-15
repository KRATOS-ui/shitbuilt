package com.example.pasaas2.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserProfileResponse {
    private UUID id;
    private String name;
    private String email;
    private String subscriptionStatus;
    private LocalDate subscriptionEndDate;
    private Integer pausedDaysRemaining;
    private String effectivePlan;
    private List<MembershipInfo> memberships;
}
