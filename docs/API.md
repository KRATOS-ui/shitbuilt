# Contrato de la API de SyncFlow

Base: `http://localhost:8080/api`. Todas las respuestas (éxito y error) usan el sobre `{ "message": string, "data": any|null }`.
Autenticación: `Authorization: Bearer <accessToken>` salvo `/auth/**`. Fechas: `LocalDateTime` ISO sin zona (`2026-11-20T09:00:00`), `LocalDate` `2026-12-31`.
Códigos de error: 400 validación/regla de negocio, 401 no autenticado o token vencido, 403 sin permiso (rol u organización), 404 no encontrado, 502 fallo del servicio de IA.

## Auth
| Método y ruta | Body | `data` |
|---|---|---|
| POST `/auth/register` | `{name,email,password(min 6),acceptedPrivacyPolicy:true}` (si es false/ausente → 400) | `AuthResponse` |
| POST `/auth/login` | `{email,password}` | `AuthResponse` |
| POST `/auth/refresh` | `{refreshToken}` | `AuthResponse` (par nuevo) |

`AuthResponse = { token, refreshToken, type:"Bearer" }`. `token` (access) dura 1 hora; `refreshToken` 7 días y solo sirve en `/auth/refresh`.

## Cuenta y suscripciones
- GET `/users/me` → `{id,name,email,subscriptionStatus("INACTIVE"|"ACTIVE"|"PAUSED"),subscriptionEndDate|null,pausedDaysRemaining,effectivePlan("FREE"|"INDIVIDUAL"|"ORGANIZATION"),memberships:[MembershipInfo]}`
- PUT `/users/me` body `{name}` → igual que GET.
- DELETE `/users/me` → `data:null`. Borra la cuenta y sus datos (derecho de supresión, Ley 1581). 400 si es ADMIN de una organización con otros miembros activos.
- POST `/subscriptions/individual` body `{months: 1..12}` → `SubscriptionInfo`. (Pago simulado.) Si el usuario es miembro activo de una organización, los días se suman a la suscripción pausada.
- GET `/subscriptions/me` → `SubscriptionInfo = {status,endDate|null,pausedDaysRemaining,effectivePlan}`

`MembershipInfo = {organizationId,organizationName,role("ADMIN"|"EMPLOYEE"),joinedAt,plan("FREE_TIER"|"BUSINESS"),planEndDate|null}`

## Organizaciones
- POST `/organizations` `{name}` → `{id,name,plan,planEndDate,createdAt}`; el creador queda ADMIN.
- GET `/organizations/mine` → `[MembershipInfo]` (solo membresías activas).
- POST `/organizations/join` `{code}` → `MembershipResponse {id,role,joinedAt,organizationName,userEmail}`. 400 si el código no existe, expiró, agotó usos o ya eres miembro activo. Pausa la suscripción individual activa (CU-01).
- POST `/organizations/{id}/leave` → `data:null`. Reanuda la suscripción pausada: `endDate = hoy + pausedDaysRemaining`. 400 si eres el único ADMIN.
- DELETE `/organizations/{id}/members/{userId}` (ADMIN) → `data:null`. Retira a un empleado (cierra su membresía y reanuda su suscripción individual pausada). 400 si es ADMIN.
- GET `/organizations/{id}/members` (ADMIN) → `[{userId,name,email,role,joinedAt}]` (solo activos).
- POST `/organizations/{id}/invite-codes` (ADMIN) `{maxUses>=1}` → `InviteCodeResponse {id,code,maxUses,usesCount,expiresAt,createdAt}`.
- GET `/organizations/{id}/invite-codes` (ADMIN) → `[InviteCodeResponse]`.
- PUT `/organizations/{id}/subscription` (ADMIN) `{plan:"BUSINESS"|"FREE_TIER",months:1..12 (obligatorio si BUSINESS)}` → `{id,name,plan,planEndDate,createdAt}`. (Pago simulado.)
- GET `/organizations/{id}/events` (ADMIN) → `[OrgEventResponse]` calendario general de la organización.
- POST `/organizations/{id}/events` (ADMIN) → crea evento organizacional y lo asigna (CU-02). Body: `EventRequest` + `userIds:[uuid]` (mínimo 1; todos deben ser miembros activos de esa organización, si no 400 y no se crea nada). `data = {event:EventResponse, assignments:[AssignmentResult]}`.

`OrgEventResponse = EventResponse + {assignees:[{userId,name,email,hasConflict}]}`

## Eventos
`RecurrenceRequest/Info = {frequency:"DAILY"|"WEEKLY"|"MONTHLY", intervalCount:int>=1 (default 1), daysOfWeek:["MONDAY",...]|null (solo WEEKLY; si es null usa el día de la semana del inicio), untilDate:LocalDate|null (null = sin fin)}`. `recurrence: null` = sin recurrencia.

`EventRequest = {title(req),description,startTime(req),endTime(req, estrictamente posterior a startTime),recurrence:RecurrenceRequest|null}`

`EventResponse = {id,title,description,startTime,endTime,hasConflict,recurrenceFrequency|null,intervalCount|null,daysOfWeek|null,untilDate|null,organizationId|null,organizationName|null,createdByName,owner:boolean}`
(`hasConflict` es el de la asignación del usuario que consulta. `owner` = el usuario creó el evento.)

- GET `/events` → `[EventResponse]` eventos base (propios + asignados).
- GET `/events/occurrences?from=<LocalDateTime>&to=<LocalDateTime>` → `[OccurrenceResponse]`: las ocurrencias ya expandidas (motor de recurrencia) que se solapan con el rango. Máximo 2000.
  `OccurrenceResponse = EventResponse + {eventId (= id), occurrenceStart, occurrenceEnd, recurring:boolean}`. Para eventos no recurrentes `occurrenceStart == startTime`.
- POST `/events` `EventRequest` → `EventResponse` (evento personal; el creador queda asignado).
- PUT `/events/{id}` `EventRequest` → `EventResponse`. Solo el creador (403 si no). Reemplaza la recurrencia (`null` la elimina). Recalcula conflictos de todos los asignados y notifica por correo.
- DELETE `/events/{id}`: el creador elimina el evento para todos (notifica cancelación); un asignado no creador solo se quita su asignación.
- POST `/events/assign` `{eventId,userIds,organizationId?}` (ADMIN de la organización del evento) → `[AssignmentResult]`. Asigna un evento ya existente; los usuarios deben ser miembros activos de esa organización (si no, `success:false` y mensaje en `error`). Si el evento era personal queda ligado a la organización.
  `AssignmentResult = {userId,userEmail,hasConflict,success,error|null}`
- POST `/events/preview` `{prompt}` → `EventPreview = {title|null,description|null,startTime|null,endTime|null,recurrence|null,missingFields:["title"|"startTime"|"endTime"],complete:boolean}`. NO guarda nada: el cliente muestra el borrador editable y, tras confirmación explícita, llama a `POST /events` o `POST /organizations/{id}/events`. 502 si la IA falla o excede el tiempo (5 s).
- GET/POST `/events/{id}/timeline` (igual que hoy): POST `{content,entryType:"COMMENT"}`; respuesta `TimelineResponse {id,content,entryType,authorName,createdAt}`.
- GET `/events/{id}/checklist` → `[{id,description,completed,completedAt}]`; POST `{description}` → item; PATCH `/events/{id}/checklist/{itemId}` `{completed:boolean}` → item; DELETE `/events/{id}/checklist/{itemId}`. Permiso: creador o asignado.

## Errores
Formato `{success:false,message}`. Códigos: 400 validación/regla de negocio, 401 sin token o token inválido/expirado, 403 sin permiso (rol o no creador), 404 recurso inexistente, 405 método no permitido, 409 violación de integridad, 502 fallo de la IA. Si `GEMINI_API_KEY` no está configurada, `/events/preview` responde 502 con un mensaje claro y el usuario puede crear el evento manualmente.
