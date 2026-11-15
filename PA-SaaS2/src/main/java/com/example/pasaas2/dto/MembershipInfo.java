package com.example.pasaas2.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MembershipInfo {
    private UUID organizationId;
    private String organizationName;
    private String role;
    private LocalDateTime joinedAt;
    private String plan;
    private LocalDate planEndDate;
}
