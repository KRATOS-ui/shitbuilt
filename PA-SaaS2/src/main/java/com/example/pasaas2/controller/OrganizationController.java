package com.example.pasaas2.controller;

import com.example.pasaas2.dto.ApiResponse;
import com.example.pasaas2.dto.EventResponse;
import com.example.pasaas2.dto.InviteCodeRequest;
import com.example.pasaas2.dto.InviteCodeResponse;
import com.example.pasaas2.dto.JoinOrganizationRequest;
import com.example.pasaas2.dto.MemberResponse;
import com.example.pasaas2.dto.MembershipInfo;
import com.example.pasaas2.dto.MembershipResponse;
import com.example.pasaas2.dto.OrganizationEventRequest;
import com.example.pasaas2.dto.OrganizationEventResult;
import com.example.pasaas2.dto.OrganizationRequest;
import com.example.pasaas2.dto.OrganizationResponse;
import com.example.pasaas2.dto.OrganizationSubscriptionRequest;
import com.example.pasaas2.model.User;
import com.example.pasaas2.service.EventService;
import com.example.pasaas2.service.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;
    private final EventService eventService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrganizationResponse>> createOrganization(
            @Valid @RequestBody OrganizationRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Organización creada exitosamente",
                organizationService.createOrganization(request, currentUser)));
    }

    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<List<MembershipInfo>>> myOrganizations(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Organizaciones obtenidas",
                organizationService.listMyMemberships(currentUser)));
    }

    @PostMapping("/join")
    public ResponseEntity<ApiResponse<MembershipResponse>> joinOrganization(
            @Valid @RequestBody JoinOrganizationRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Te has unido a la organización exitosamente",
                organizationService.joinOrganization(request, currentUser)));
    }

    @PostMapping("/{organizationId}/leave")
    public ResponseEntity<ApiResponse<Void>> leave(
            @PathVariable UUID organizationId,
            @AuthenticationPrincipal User currentUser) {
        organizationService.leaveOrganization(organizationId, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Saliste de la organización", null));
    }

    // ---- Solo ADMIN ----

    @GetMapping("/{organizationId}/members")
    public ResponseEntity<ApiResponse<List<MemberResponse>>> members(
            @PathVariable UUID organizationId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Miembros obtenidos",
                organizationService.listMembers(organizationId, currentUser)));
    }

    @DeleteMapping("/{organizationId}/members/{userId}")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable UUID organizationId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal User currentUser) {
        organizationService.removeMember(organizationId, userId, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Empleado retirado de la organización", null));
    }

    @PostMapping("/{organizationId}/invite-codes")
    public ResponseEntity<ApiResponse<InviteCodeResponse>> generateInviteCode(
            @PathVariable UUID organizationId,
            @Valid @RequestBody InviteCodeRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Código generado",
                organizationService.generateInviteCode(organizationId, request, currentUser)));
    }

    @GetMapping("/{organizationId}/invite-codes")
    public ResponseEntity<ApiResponse<List<InviteCodeResponse>>> inviteCodes(
            @PathVariable UUID organizationId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Códigos obtenidos",
                organizationService.listInviteCodes(organizationId, currentUser)));
    }

    @PutMapping("/{organizationId}/subscription")
    public ResponseEntity<ApiResponse<OrganizationResponse>> updateSubscription(
            @PathVariable UUID organizationId,
            @Valid @RequestBody OrganizationSubscriptionRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Suscripción de la organización actualizada (pago simulado)",
                organizationService.updateSubscription(organizationId, request, currentUser)));
    }

    @GetMapping("/{organizationId}/events")
    public ResponseEntity<ApiResponse<List<EventResponse>>> organizationEvents(
            @PathVariable UUID organizationId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Calendario de la organización obtenido",
                eventService.getOrganizationEvents(organizationId, currentUser)));
    }

    @PostMapping("/{organizationId}/events")
    public ResponseEntity<ApiResponse<OrganizationEventResult>> createOrganizationEvent(
            @PathVariable UUID organizationId,
            @Valid @RequestBody OrganizationEventRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Evento organizacional creado",
                eventService.createOrganizationEvent(organizationId, request, currentUser)));
    }
}
