package com.example.pasaas2.exception;

/** Fallo o tiempo de espera de un servicio externo, p. ej. la IA (HTTP 502). */
public class ExternalServiceException extends RuntimeException {
    public ExternalServiceException(String message) {
        super(message);
    }
}
