package com.example.pasaas2.notification;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Habilita @Async para que el envío de correos no bloquee la petición del usuario (RNF rendimiento). */
@Configuration
@EnableAsync
public class AsyncConfig {
}
