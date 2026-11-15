package com.example.pasaas2.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InviteCodeResponse {
    private UUID id;
    private String code;
    private Integer maxUses;
    private Integer usesCount;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
