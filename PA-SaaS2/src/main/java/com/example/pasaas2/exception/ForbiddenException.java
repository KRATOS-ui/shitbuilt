package com.example.pasaas2.exception;

/** El usuario está autenticado pero no tiene permiso por su rol u organización (HTTP 403). */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
