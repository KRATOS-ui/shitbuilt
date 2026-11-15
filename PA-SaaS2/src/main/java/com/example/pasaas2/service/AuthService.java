package com.example.pasaas2.service;

import com.example.pasaas2.config.JwtService;
import com.example.pasaas2.dto.AuthRequest;
import com.example.pasaas2.dto.AuthResponse;
import com.example.pasaas2.dto.RefreshRequest;
import com.example.pasaas2.dto.RegisterRequest;
import com.example.pasaas2.exception.BusinessException;
import com.example.pasaas2.exception.UnauthorizedException;
import com.example.pasaas2.model.User;
import com.example.pasaas2.repository.UserRepository;
import com.example.pasaas2.subscription.SubscriptionCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthResponse register(RegisterRequest request) {
        // Ley 1581 de 2012: sin aceptación expresa de la política de datos no se crea la cuenta
        if (!Boolean.TRUE.equals(request.getAcceptedPrivacyPolicy())) {
            throw new BusinessException("Debes aceptar la política de tratamiento de datos personales para registrarte");
        }
        if (userRepository.existsByEmailIgnoreCase(request.getEmail().trim())) {
            throw new BusinessException("El email ya está registrado");
        }

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword())) // BCrypt (RNF-01)
                // Los usuarios nuevos inician sin suscripción (plan básico)
                .subscriptionStatus(SubscriptionCalculator.INACTIVE)
                .pausedDaysRemaining(0)
                .privacyAcceptedAt(LocalDateTime.now())
                .build();

        userRepository.save(user);

        return tokensFor(user);
    }

    public AuthResponse login(AuthRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Usuario no encontrado"));

        return tokensFor(user);
    }

    /** Canjea un refresh token válido por un par nuevo de tokens (RNF-03). */
    public AuthResponse refresh(RefreshRequest request) {
        try {
            String token = request.getRefreshToken();
            if (!jwtService.isRefreshToken(token)) {
                throw new UnauthorizedException("El token enviado no es un refresh token");
            }
            String email = jwtService.extractUsername(token);
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new UnauthorizedException("Usuario no encontrado"));
            if (!jwtService.isTokenValid(token, user)) {
                throw new UnauthorizedException("La sesión expiró. Inicia sesión de nuevo.");
            }
            return tokensFor(user);
        } catch (UnauthorizedException e) {
            throw e;
        } catch (Exception e) {
            throw new UnauthorizedException("La sesión expiró. Inicia sesión de nuevo.");
        }
    }

    private AuthResponse tokensFor(User user) {
        return AuthResponse.builder()
                .token(jwtService.generateToken(user))
                .refreshToken(jwtService.generateRefreshToken(user))
                .build();
    }
}
