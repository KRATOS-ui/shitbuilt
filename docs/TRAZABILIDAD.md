# Matriz de trazabilidad – SyncFlow

Rutas relativas a `PA-SaaS2/src/main/java/com/example/pasaas2/` (backend) y `frontend-saas/src/` (frontend).

## Requisitos funcionales
| Id | Implementación |
|---|---|
| RF-01 Registro | `AuthService.register`, `POST /auth/register`; `pages/Register.jsx` (con aceptación de política de datos) |
| RF-02 Login | `AuthService.login`, `POST /auth/login`, `POST /auth/refresh`; `pages/Login.jsx` |
| RF-03 Agregar eventos | `EventService.createPersonalEvent`, `POST /events`; `EventEditorModal.jsx` |
| RF-04 Modificar eventos | `EventService.updateEvent`, `PUT /events/{id}` (solo creador) |
| RF-05 Eliminar eventos | `EventService.deleteEvent`, `DELETE /events/{id}` |
| RF-06 Restricción por rol | `OrganizationService.requireAdmin`; `ProtectedRoute`, paneles admin en `components/org/` |
| RF-07 Evento por prompt | `GeminiAiService`, `AiDraftParser`, `POST /events/preview`; borrador editable en `EventEditorModal` |
| RF-08 Notificación por correo | `notification/NotificationService`, `MailDispatcher` (asignado, modificado, cancelado) |
| RF-09 Crear organización | `OrganizationService.create`, `POST /organizations` |
| RF-10 Código de invitación con límite de usos | `OrganizationService` invite codes (bloqueo pesimista), `POST /organizations/{id}/invite-codes` |
| RF-11 Asignar evento a empleados | `EventService.createOrganizationEvent`, `assignEventToUsers`, `POST /organizations/{id}/events` |
| RF-12 Recurrencia | `recurrence/RecurrenceEngine` (DAILY/WEEKLY con días/MONTHLY, cada N, hasta fecha); `GET /events/occurrences`; `RecurrenceFields.jsx` |
| RF-13 Suscripciones diferenciadas | `SubscriptionService`, `SubscriptionCalculator`, `PUT /organizations/{id}/subscription`, `POST /subscriptions/individual` (pago simulado) |

## Casos de uso
| Id | Implementación |
|---|---|
| CU-01 Unirse por código | `OrganizationService.join` pausa la suscripción individual (`pausedDaysRemaining`); `leave`/`removeMember` → `closeMembership` la reanuda (`hoy + días`). Código inválido/expirado/agotado → 400 |
| CU-02 Asignar recurrente | Usuario fuera de la organización → rechazado (400, nada se crea); solapamiento → se permite y se marca `hasConflict`, alerta amarilla en calendario y correo distinto al estándar |
| CU-03 Evento por prompt | Preview sin persistir; campos faltantes (`missingFields`) completables en el mismo borrador; se puede editar, descartar o reintentar; solo se guarda al confirmar |

## Requisitos no funcionales
| Id | Implementación |
|---|---|
| Seguridad 01 Hash | BCrypt (`SecurityConfig` `PasswordEncoder`) |
| Seguridad 02 Aislamiento entre organizaciones | Verificación de membresía activa en cada operación de `OrganizationService`/`EventService` |
| Seguridad 03 Tokens + refresh | `JwtService` (acceso 1 h, refresh 7 d), `POST /auth/refresh`, interceptor en `services/api.js` |
| Rendimiento 01 Correo ≤ 5 min | Envío asíncrono inmediato tras commit, 3 reintentos |
| Privacidad 01 Ley 1581/2012 | Consentimiento `privacyAcceptedAt`, página `pages/Privacy.jsx`, ver/editar perfil y `DELETE /users/me` (`UserAccountService`) |
| Disponibilidad 01 ≥ 95 % | `restart: unless-stopped`, healthchecks (`/actuator/health`) en `docker-compose.yaml` |
| Usabilidad 01 Prompt ≤ 5 s | Timeout de 5 s en `GeminiAiService`; falla con 502 y se puede continuar manualmente |
| Escalabilidad 01 | Backend sin estado (JWT), índices en `Event`, `EventAssignment`, `Membership`, consultas acotadas por organización |
| Mantenibilidad 01 Pruebas automatizadas | `src/test/java/.../RecurrenceEngineTest` (14), `SubscriptionCalculatorTest` (8), `AiDraftParserTest` (11) |

## Limitaciones conocidas
- La detección de conflictos compara los eventos base, no cada ocurrencia de una serie recurrente.
- Los pagos son simulados; el plan efectivo es informativo (no bloquea funciones).
- El calendario organizacional se muestra como tabla; la asignación a eventos ya existentes está en la API (`POST /events/assign`) pero la interfaz asigna al crear.
- La disponibilidad del 95 % depende del entorno de despliegue elegido.
