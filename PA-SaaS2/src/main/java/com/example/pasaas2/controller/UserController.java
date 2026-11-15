package com.example.pasaas2.controller;

import com.example.pasaas2.dto.ApiResponse;
import com.example.pasaas2.dto.UpdateProfileRequest;
import com.example.pasaas2.dto.UserProfileResponse;
import com.example.pasaas2.model.User;
import com.example.pasaas2.service.UserAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserAccountService userAccountService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> me(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Perfil obtenido", userAccountService.getProfile(currentUser)));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMe(
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Perfil actualizado",
                userAccountService.updateName(currentUser, request.getName())));
    }

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> deleteMe(@AuthenticationPrincipal User currentUser) {
        userAccountService.deleteAccount(currentUser);
        return ResponseEntity.ok(ApiResponse.success("Cuenta eliminada", null));
    }
}
