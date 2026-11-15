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
public class ChecklistItemResponse {
    private UUID id;
    private String description;
    private Boolean completed;
    private LocalDateTime completedAt;
}
