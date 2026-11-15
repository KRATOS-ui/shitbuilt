package com.example.pasaas2.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Envía correos en un hilo aparte. Si no hay SMTP configurado (MAIL_HOST vacío) o el envío está
 * desactivado, el correo se registra en el log para poder verificar el flujo sin servidor de correo.
 * Reintenta hasta 3 veces para cumplir el máximo de 5 minutos del RNF de rendimiento.
 */
@Slf4j
@Component
public class MailDispatcher {

    private static final int MAX_ATTEMPTS = 3;

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String from;
    private final String host;
    private final boolean enabled;

    public MailDispatcher(ObjectProvider<JavaMailSender> mailSenderProvider,
                          @Value("${app.mail.from:no-reply@syncflow.local}") String from,
                          @Value("${spring.mail.host:}") String host,
                          @Value("${app.mail.enabled:true}") boolean enabled) {
        this.mailSenderProvider = mailSenderProvider;
        this.from = from;
        this.host = host;
        this.enabled = enabled;
    }

    @Async
    public void dispatch(EmailMessage message) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (!enabled || sender == null || host == null || host.isBlank()) {
            log.info("[CORREO SIMULADO] Para: {} | Asunto: {}\n{}", message.to(), message.subject(), message.body());
            return;
        }

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                SimpleMailMessage mail = new SimpleMailMessage();
                mail.setFrom(from);
                mail.setTo(message.to());
                mail.setSubject(message.subject());
                mail.setText(message.body());
                sender.send(mail);
                log.info("Correo enviado a {} ({})", message.to(), message.subject());
                return;
            } catch (Exception e) {
                log.warn("Fallo al enviar correo a {} (intento {}/{}): {}", message.to(), attempt, MAX_ATTEMPTS, e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    try {
                        Thread.sleep(2000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
        log.error("No se pudo enviar el correo a {} tras {} intentos", message.to(), MAX_ATTEMPTS);
    }
}
