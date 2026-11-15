package com.example.pasaas2.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AssignmentResultResponse {
    private UUID userId;
    private String userEmail;
    private Boolean hasConflict;
    private Boolean success;
    private String error;
}
