package com.example.pasaas2.exception;

/** Regla de negocio incumplida o dato inválido (HTTP 400). */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
