package com.example.pasaas2.exception;

/** Token ausente, inválido o vencido (HTTP 401). */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
