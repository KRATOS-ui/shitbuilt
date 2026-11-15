package com.example.pasaas2.controller;

import com.example.pasaas2.dto.ApiResponse;
import com.example.pasaas2.dto.AssignmentResultResponse;
import com.example.pasaas2.dto.ChecklistCreateRequest;
import com.example.pasaas2.dto.ChecklistItemResponse;
import com.example.pasaas2.dto.ChecklistToggleRequest;
import com.example.pasaas2.dto.EventAssignRequest;
import com.example.pasaas2.dto.EventPreview;
import com.example.pasaas2.dto.EventPromptRequest;
import com.example.pasaas2.dto.EventRequest;
import com.example.pasaas2.dto.EventResponse;
import com.example.pasaas2.dto.TimelineRequest;
import com.example.pasaas2.dto.TimelineResponse;
import com.example.pasaas2.model.User;
import com.example.pasaas2.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<EventResponse>>> getUserEvents(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Eventos obtenidos exitosamente",
                eventService.getEventsForUser(currentUser)));
    }

    @GetMapping("/occurrences")
    public ResponseEntity<ApiResponse<List<EventResponse>>> getOccurrences(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Ocurrencias obtenidas exitosamente",
                eventService.getOccurrences(currentUser, from, to)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EventResponse>> createPersonalEvent(
            @Valid @RequestBody EventRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Evento creado exitosamente",
                eventService.createPersonalEvent(request, currentUser)));
    }

    @PutMapping("/{eventId}")
    public ResponseEntity<ApiResponse<EventResponse>> updateEvent(
            @PathVariable UUID eventId,
            @Valid @RequestBody EventRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Evento actualizado exitosamente",
                eventService.updateEvent(eventId, request, currentUser)));
    }

    @DeleteMapping("/{eventId}")
    public ResponseEntity<ApiResponse<Void>> deleteEvent(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal User currentUser) {
        eventService.deleteEvent(eventId, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Evento eliminado exitosamente", null));
    }

    @PostMapping("/assign")
    public ResponseEntity<ApiResponse<List<AssignmentResultResponse>>> assignEventToUsers(
            @Valid @RequestBody EventAssignRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Proceso de asignación completado",
                eventService.assignEventToUsers(request, currentUser)));
    }

    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<EventPreview>> previewEvent(
            @Valid @RequestBody EventPromptRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Borrador generado exitosamente",
                eventService.previewEventFromText(request)));
    }

    // ---- Contexto del evento ----

    @PostMapping("/{eventId}/timeline")
    public ResponseEntity<ApiResponse<TimelineResponse>> addTimelineEntry(
            @PathVariable UUID eventId,
            @Valid @RequestBody TimelineRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Entrada agregada al contexto del evento",
                eventService.addTimelineEntry(eventId, request, currentUser)));
    }

    @GetMapping("/{eventId}/timeline")
    public ResponseEntity<ApiResponse<List<TimelineResponse>>> getTimeline(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Contexto obtenido exitosamente",
                eventService.getTimelineForEvent(eventId, currentUser)));
    }

    // ---- Checklist ----

    @GetMapping("/{eventId}/checklist")
    public ResponseEntity<ApiResponse<List<ChecklistItemResponse>>> getChecklist(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Checklist obtenido",
                eventService.getChecklist(eventId, currentUser)));
    }

    @PostMapping("/{eventId}/checklist")
    public ResponseEntity<ApiResponse<ChecklistItemResponse>> addChecklistItem(
            @PathVariable UUID eventId,
            @Valid @RequestBody ChecklistCreateRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Tarea agregada",
                eventService.addChecklistItem(eventId, request, currentUser)));
    }

    @PatchMapping("/{eventId}/checklist/{itemId}")
    public ResponseEntity<ApiResponse<ChecklistItemResponse>> toggleChecklistItem(
            @PathVariable UUID eventId,
            @PathVariable UUID itemId,
            @Valid @RequestBody ChecklistToggleRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Tarea actualizada",
                eventService.setChecklistItemCompleted(eventId, itemId, request.getCompleted(), currentUser)));
    }

    @DeleteMapping("/{eventId}/checklist/{itemId}")
    public ResponseEntity<ApiResponse<Void>> deleteChecklistItem(
            @PathVariable UUID eventId,
            @PathVariable UUID itemId,
            @AuthenticationPrincipal User currentUser) {
        eventService.deleteChecklistItem(eventId, itemId, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Tarea eliminada", null));
    }
}
