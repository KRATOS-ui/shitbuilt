package com.example.pasaas2.controller;

import com.example.pasaas2.dto.ApiResponse;
import com.example.pasaas2.dto.IndividualSubscriptionRequest;
import com.example.pasaas2.dto.SubscriptionInfo;
import com.example.pasaas2.model.User;
import com.example.pasaas2.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<SubscriptionInfo>> mySubscription(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Suscripción obtenida",
                subscriptionService.getSubscription(currentUser)));
    }

    @PostMapping("/individual")
    public ResponseEntity<ApiResponse<SubscriptionInfo>> purchaseIndividual(
            @Valid @RequestBody IndividualSubscriptionRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Suscripción individual activada (pago simulado)",
                subscriptionService.purchaseIndividual(currentUser, request.getMonths())));
    }
}
