package com.example.pasaas2.notification;

import com.example.pasaas2.model.Event;
import com.example.pasaas2.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Construye y encola las notificaciones por correo de eventos (RF-08).
 * Los correos se arman dentro de la transacción (con datos planos) y se envían solo después del commit,
 * así nunca se avisa de algo que luego se revirtió.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy, HH:mm", Locale.forLanguageTag("es-CO"));

    private final MailDispatcher mailDispatcher;

    /** Evento asignado. Si hay conflicto de horario se envía un correo distinto al estándar (CU-02). */
    public void eventAssigned(Event event, User recipient, User actor, boolean conflict) {
        if (skip(recipient, actor)) {
            return;
        }
        String when = describe(event);
        String org = organizationName(event);
        if (conflict) {
            queue(new EmailMessage(recipient.getEmail(),
                    "SyncFlow: nuevo evento asignado (se solapa con otro compromiso)",
                    greeting(recipient)
                            + actorName(actor) + " te asignó el evento \"" + event.getTitle() + "\"" + org + ".\n\n"
                            + when + "\n\n"
                            + "ATENCIÓN: la fecha asignada se solapa con un compromiso personal que ya tenías registrado. "
                            + "El evento quedó asignado igualmente; revisa tu calendario y decide cómo resolver el cruce.\n\n"
                            + signature()));
        } else {
            queue(new EmailMessage(recipient.getEmail(),
                    "SyncFlow: nuevo evento asignado",
                    greeting(recipient)
                            + actorName(actor) + " te asignó el evento \"" + event.getTitle() + "\"" + org + ".\n\n"
                            + when + "\n\n"
                            + signature()));
        }
    }

    public void eventUpdated(Event event, User recipient, User actor, boolean conflict) {
        if (skip(recipient, actor)) {
            return;
        }
        String extra = conflict
                ? "\nATENCIÓN: con el cambio, este evento se solapa con otro compromiso que ya tenías registrado.\n"
                : "";
        queue(new EmailMessage(recipient.getEmail(),
                "SyncFlow: se modificó un evento",
                greeting(recipient)
                        + actorName(actor) + " modificó el evento \"" + event.getTitle() + "\"" + organizationName(event) + ".\n\n"
                        + describe(event) + "\n" + extra + "\n"
                        + signature()));
    }

    public void eventCancelled(Event event, User recipient, User actor) {
        if (skip(recipient, actor)) {
            return;
        }
        queue(new EmailMessage(recipient.getEmail(),
                "SyncFlow: se canceló un evento",
                greeting(recipient)
                        + actorName(actor) + " canceló el evento \"" + event.getTitle() + "\"" + organizationName(event) + ".\n\n"
                        + "Estaba programado para: " + event.getStartTime().format(FORMAT) + ".\n\n"
                        + signature()));
    }

    private boolean skip(User recipient, User actor) {
        return recipient == null
                || recipient.getEmail() == null
                || (actor != null && actor.getId() != null && actor.getId().equals(recipient.getId()));
    }

    private String describe(Event event) {
        LocalDateTime start = event.getStartTime();
        LocalDateTime end = event.getEndTime();
        String text = "Inicio: " + start.format(FORMAT) + "\nFin: " + end.format(FORMAT);
        if (event.getDescription() != null && !event.getDescription().isBlank()) {
            text += "\nDetalle: " + event.getDescription();
        }
        return text;
    }

    private String organizationName(Event event) {
        return event.getOrganization() != null ? " de " + event.getOrganization().getName() : "";
    }

    private String actorName(User actor) {
        return actor != null && actor.getName() != null ? actor.getName() : "Un administrador";
    }

    private String greeting(User recipient) {
        return "Hola " + recipient.getName() + ",\n\n";
    }

    private String signature() {
        return "— SyncFlow";
    }

    private void queue(EmailMessage message) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    mailDispatcher.dispatch(message);
                }
            });
        } else {
            mailDispatcher.dispatch(message);
        }
    }
}
