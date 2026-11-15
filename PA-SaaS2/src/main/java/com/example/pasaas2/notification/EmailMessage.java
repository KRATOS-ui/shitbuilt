package com.example.pasaas2.notification;

/** Correo ya construido (solo datos planos, sin entidades JPA) para poder enviarlo en otro hilo. */
public record EmailMessage(String to, String subject, String body) {
}
