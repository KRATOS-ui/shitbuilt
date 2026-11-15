package com.example.pasaas2.service;

import com.example.pasaas2.dto.AssigneeInfo;
import com.example.pasaas2.dto.AssignmentResultResponse;
import com.example.pasaas2.dto.ChecklistCreateRequest;
import com.example.pasaas2.dto.ChecklistItemResponse;
import com.example.pasaas2.dto.EventAssignRequest;
import com.example.pasaas2.dto.EventPreview;
import com.example.pasaas2.dto.EventPromptRequest;
import com.example.pasaas2.dto.EventRequest;
import com.example.pasaas2.dto.EventResponse;
import com.example.pasaas2.dto.OrganizationEventRequest;
import com.example.pasaas2.dto.OrganizationEventResult;
import com.example.pasaas2.dto.RecurrenceRequest;
import com.example.pasaas2.dto.TimelineRequest;
import com.example.pasaas2.dto.TimelineResponse;
import com.example.pasaas2.exception.BusinessException;
import com.example.pasaas2.exception.ForbiddenException;
import com.example.pasaas2.exception.ResourceNotFoundException;
import com.example.pasaas2.exception.UnauthorizedException;
import com.example.pasaas2.model.ChecklistItem;
import com.example.pasaas2.model.Event;
import com.example.pasaas2.model.EventAssignment;
import com.example.pasaas2.model.Membership;
import com.example.pasaas2.model.Organization;
import com.example.pasaas2.model.RecurrenceRule;
import com.example.pasaas2.model.TimelineEntry;
import com.example.pasaas2.model.User;
import com.example.pasaas2.notification.NotificationService;
import com.example.pasaas2.recurrence.RecurrenceEngine;
import com.example.pasaas2.repository.ChecklistItemRepository;
import com.example.pasaas2.repository.EventAssignmentRepository;
import com.example.pasaas2.repository.EventRepository;
import com.example.pasaas2.repository.MembershipRepository;
import com.example.pasaas2.repository.RecurrenceRuleRepository;
import com.example.pasaas2.repository.TimelineEntryRepository;
import com.example.pasaas2.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventService {

    private static final int MAX_RANGE_DAYS = 400;

    private final EventRepository eventRepository;
    private final EventAssignmentRepository eventAssignmentRepository;
    private final UserRepository userRepository;
    private final RecurrenceRuleRepository recurrenceRuleRepository;
    private final TimelineEntryRepository timelineEntryRepository;
    private final ChecklistItemRepository checklistItemRepository;
    private final MembershipRepository membershipRepository;
    private final GeminiAiService geminiAiService;
    private final OrganizationService organizationService;
    private final NotificationService notificationService;

    // =====================================================================================
    // Creación
    // =====================================================================================

    /** Evento personal (RF-03). El creador queda asignado para verlo en su calendario. */
    @Transactional
    public EventResponse createPersonalEvent(EventRequest request, User principal) {
        User creator = reload(principal);
        validateTimes(request);
        RecurrenceEngine.Spec spec = toSpec(request.getRecurrence());

        Event event = eventRepository.save(Event.builder()
                .title(request.getTitle().trim())
                .description(request.getDescription())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .createdBy(creator)
                .build());

        RecurrenceRule rule = saveRule(event, spec);

        boolean conflict = eventAssignmentRepository.hasOverlappingEvents(
                creator, request.getStartTime(), request.getEndTime());
        assign(event, creator, conflict);

        return toResponse(event, rule, conflict, creator);
    }

    /**
     * CU-02: el administrador crea un evento de su organización y lo asigna a empleados.
     * Todos los destinatarios se validan ANTES de crear nada: si alguno no pertenece a la organización,
     * se rechaza la operación completa (flujo alternativo del caso de uso).
     */
    @Transactional
    public OrganizationEventResult createOrganizationEvent(UUID organizationId, OrganizationEventRequest request,
                                                           User principal) {
        User admin = reload(principal);
        Membership adminMembership = organizationService.requireAdmin(admin, organizationId);
        Organization org = adminMembership.getOrganization();

        validateTimes(request);
        RecurrenceEngine.Spec spec = toSpec(request.getRecurrence());

        Map<UUID, User> targets = new LinkedHashMap<>();
        for (UUID userId : request.getUserIds()) {
            if (userId.equals(admin.getId())) {
                continue; // el creador ya queda asignado
            }
            User target = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException("Usuario no encontrado: " + userId));
            if (!membershipRepository.existsByUserAndOrganizationAndLeftAtIsNull(target, org)) {
                throw new BusinessException("El usuario " + target.getEmail() + " no pertenece a tu organización");
            }
            targets.put(userId, target);
        }
        if (targets.isEmpty()) {
            throw new BusinessException("Debes seleccionar al menos un empleado de la organización");
        }

        Event event = eventRepository.save(Event.builder()
                .title(request.getTitle().trim())
                .description(request.getDescription())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .createdBy(admin)
                .organization(org)
                .build());
        RecurrenceRule rule = saveRule(event, spec);

        boolean adminConflict = eventAssignmentRepository.hasOverlappingEvents(
                admin, request.getStartTime(), request.getEndTime());
        assign(event, admin, adminConflict);

        List<AssignmentResultResponse> results = new ArrayList<>();
        for (User target : targets.values()) {
            boolean conflict = eventAssignmentRepository.hasOverlappingEvents(
                    target, request.getStartTime(), request.getEndTime());
            assign(event, target, conflict);
            notificationService.eventAssigned(event, target, admin, conflict);
            results.add(AssignmentResultResponse.builder()
                    .userId(target.getId())
                    .userEmail(target.getEmail())
                    .hasConflict(conflict)
                    .success(true)
                    .build());
        }

        return OrganizationEventResult.builder()
                .event(toResponse(event, rule, adminConflict, admin))
                .assignments(results)
                .build();
    }

    /** Asigna un evento ya existente a empleados de la organización (solo ADMIN). */
    @Transactional
    public List<AssignmentResultResponse> assignEventToUsers(EventAssignRequest request, User principal) {
        User admin = reload(principal);
        Event event = findEvent(request.getEventId());

        UUID organizationId = resolveOrganizationId(request, event, admin);
        Membership adminMembership = organizationService.requireAdmin(admin, organizationId);
        Organization org = adminMembership.getOrganization();

        boolean isCreator = event.getCreatedBy().getId().equals(admin.getId());
        boolean inThisOrg = event.getOrganization() != null && event.getOrganization().getId().equals(organizationId);
        if (event.getOrganization() != null && !inThisOrg) {
            throw new ForbiddenException("El evento pertenece a otra organización");
        }
        if (!isCreator && !inThisOrg) {
            throw new ForbiddenException(
                    "Solo el creador del evento o un administrador de su organización puede asignarlo");
        }
        if (event.getOrganization() == null) {
            event.setOrganization(org); // el evento personal pasa a ser organizacional
            eventRepository.save(event);
        }

        List<AssignmentResultResponse> results = new ArrayList<>();
        for (UUID userId : request.getUserIds()) {
            Optional<User> found = userRepository.findById(userId);
            if (found.isEmpty()) {
                results.add(failure(userId, null, "Usuario no encontrado"));
                continue;
            }
            User target = found.get();
            if (!membershipRepository.existsByUserAndOrganizationAndLeftAtIsNull(target, org)) {
                results.add(failure(userId, target.getEmail(), "El usuario no pertenece a tu organización"));
                continue;
            }
            if (eventAssignmentRepository.findByEventAndUser(event, target).isPresent()) {
                results.add(failure(userId, target.getEmail(), "El usuario ya tiene este evento asignado"));
                continue;
            }
            boolean conflict = eventAssignmentRepository.hasOverlappingEvents(
                    target, event.getStartTime(), event.getEndTime());
            assign(event, target, conflict);
            notificationService.eventAssigned(event, target, admin, conflict);
            results.add(AssignmentResultResponse.builder()
                    .userId(target.getId())
                    .userEmail(target.getEmail())
                    .hasConflict(conflict)
                    .success(true)
                    .build());
        }
        return results;
    }

    // =====================================================================================
    // Consultas
    // =====================================================================================

    @Transactional(readOnly = true)
    public List<EventResponse> getEventsForUser(User principal) {
        User user = reload(principal);
        List<EventAssignment> assignments = eventAssignmentRepository.findAllByUser(user);
        List<Event> events = assignments.stream().map(EventAssignment::getEvent).toList();
        Map<UUID, RecurrenceRule> rules = rulesByEvent(events);

        return assignments.stream()
                .map(a -> toResponse(a.getEvent(), rules.get(a.getEvent().getId()), a.getHasConflict(), user))
                .toList();
    }

    /** Ocurrencias ya expandidas (RF-12) de los eventos del usuario que caen dentro de [from, to). */
    @Transactional(readOnly = true)
    public List<EventResponse> getOccurrences(User principal, LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new BusinessException("El rango de fechas no es válido");
        }
        if (Duration.between(from, to).toDays() > MAX_RANGE_DAYS) {
            throw new BusinessException("El rango máximo permitido es de " + MAX_RANGE_DAYS + " días");
        }

        User user = reload(principal);
        List<EventAssignment> assignments = eventAssignmentRepository.findAllByUser(user);
        Map<UUID, RecurrenceRule> rules = rulesByEvent(assignments.stream().map(EventAssignment::getEvent).toList());

        List<EventResponse> result = new ArrayList<>();
        for (EventAssignment a : assignments) {
            Event event = a.getEvent();
            RecurrenceRule rule = rules.get(event.getId());
            RecurrenceEngine.Spec spec = specOf(rule);
            List<RecurrenceEngine.Occurrence> occurrences = RecurrenceEngine.expand(
                    event.getStartTime(), event.getEndTime(), spec, from, to);
            for (RecurrenceEngine.Occurrence occ : occurrences) {
                EventResponse response = toResponse(event, rule, a.getHasConflict(), user);
                response.setEventId(event.getId());
                response.setOccurrenceStart(occ.start());
                response.setOccurrenceEnd(occ.end());
                response.setRecurring(spec != null);
                result.add(response);
                if (result.size() >= RecurrenceEngine.MAX_OCCURRENCES) {
                    break;
                }
            }
            if (result.size() >= RecurrenceEngine.MAX_OCCURRENCES) {
                break;
            }
        }
        result.sort(Comparator.comparing(EventResponse::getOccurrenceStart));
        return result;
    }

    /** Calendario general de la organización con los asignados de cada evento (solo ADMIN). */
    @Transactional(readOnly = true)
    public List<EventResponse> getOrganizationEvents(UUID organizationId, User principal) {
        User admin = reload(principal);
        Membership adminMembership = organizationService.requireAdmin(admin, organizationId);

        List<Event> events = eventRepository.findByOrganizationOrderByStartTimeAsc(adminMembership.getOrganization());
        if (events.isEmpty()) {
            return List.of();
        }
        Map<UUID, RecurrenceRule> rules = rulesByEvent(events);

        Map<UUID, List<EventAssignment>> assignmentsByEvent = new HashMap<>();
        for (EventAssignment a : eventAssignmentRepository.findAllByEventIn(events)) {
            assignmentsByEvent.computeIfAbsent(a.getEvent().getId(), k -> new ArrayList<>()).add(a);
        }

        List<EventResponse> result = new ArrayList<>();
        for (Event event : events) {
            List<EventAssignment> assignments = assignmentsByEvent.getOrDefault(event.getId(), List.of());
            Boolean adminConflict = false;
            List<AssigneeInfo> assignees = new ArrayList<>();
            for (EventAssignment a : assignments) {
                if (a.getUser().getId().equals(admin.getId())) {
                    adminConflict = a.getHasConflict();
                }
                assignees.add(AssigneeInfo.builder()
                        .userId(a.getUser().getId())
                        .name(a.getUser().getName())
                        .email(a.getUser().getEmail())
                        .hasConflict(a.getHasConflict())
                        .build());
            }
            EventResponse response = toResponse(event, rules.get(event.getId()), adminConflict, admin);
            response.setAssignees(assignees);
            result.add(response);
        }
        return result;
    }

    // =====================================================================================
    // Modificación y eliminación (RF-04)
    // =====================================================================================

    @Transactional
    public EventResponse updateEvent(UUID eventId, EventRequest request, User principal) {
        User actor = reload(principal);
        Event event = findEvent(eventId);

        // Solo quien creó el evento puede modificarlo (los asignados no)
        if (!event.getCreatedBy().getId().equals(actor.getId())) {
            throw new ForbiddenException("Solo el creador del evento puede modificarlo");
        }
        validateTimes(request);
        RecurrenceEngine.Spec spec = toSpec(request.getRecurrence());

        event.setTitle(request.getTitle().trim());
        event.setDescription(request.getDescription());
        event.setStartTime(request.getStartTime());
        event.setEndTime(request.getEndTime());
        eventRepository.save(event);

        RecurrenceRule rule = saveRule(event, spec);

        // Recalcular conflictos de todos los asignados (excluyendo el propio evento) y avisarles
        boolean actorConflict = false;
        for (EventAssignment assignment : eventAssignmentRepository.findAllByEvent(event)) {
            User assignee = assignment.getUser();
            boolean conflict = eventAssignmentRepository.hasOverlappingEventsExcluding(
                    assignee, event.getStartTime(), event.getEndTime(), event.getId());
            assignment.setHasConflict(conflict);
            eventAssignmentRepository.save(assignment);

            if (assignee.getId().equals(actor.getId())) {
                actorConflict = conflict;
            } else {
                notificationService.eventUpdated(event, assignee, actor, conflict);
            }
        }
        return toResponse(event, rule, actorConflict, actor);
    }

    /**
     * El creador elimina el evento para todos (y se les notifica). Un asignado que no es el creador
     * solo se quita su propia asignación.
     */
    @Transactional
    public void deleteEvent(UUID eventId, User principal) {
        User actor = reload(principal);
        Event event = findEvent(eventId);

        if (event.getCreatedBy().getId().equals(actor.getId())) {
            deleteEventAsCreator(event, actor);
            return;
        }
        EventAssignment assignment = eventAssignmentRepository.findByEventAndUser(event, actor)
                .orElseThrow(() -> new ForbiddenException("No tienes permiso sobre este evento"));
        eventAssignmentRepository.delete(assignment);
    }

    /** Notifica la cancelación a los asignados y borra el evento con todo lo que depende de él. */
    @Transactional
    public void deleteEventAsCreator(Event event, User actor) {
        for (EventAssignment assignment : eventAssignmentRepository.findAllByEvent(event)) {
            notificationService.eventCancelled(event, assignment.getUser(), actor);
        }
        // Orden importante por las llaves foráneas: asignaciones -> regla -> evento (cascada a checklist y contexto)
        eventAssignmentRepository.deleteByEvent(event);
        recurrenceRuleRepository.deleteByEvent(event);
        eventRepository.delete(event);
    }

    // =====================================================================================
    // IA (CU-03)
    // =====================================================================================

    /** Genera un borrador a partir de texto. No persiste nada. */
    public EventPreview previewEventFromText(EventPromptRequest request) {
        return geminiAiService.previewEventFromText(request.getPrompt());
    }

    // =====================================================================================
    // Contexto (timeline)
    // =====================================================================================

    @Transactional
    public TimelineResponse addTimelineEntry(UUID eventId, TimelineRequest request, User principal) {
        User user = reload(principal);
        Event event = findEvent(eventId);
        requireParticipant(event, user, "No tienes permisos para participar en este evento");

        TimelineEntry entry = TimelineEntry.builder()
                .event(event)
                .author(user)
                .entryType(request.getEntryType())
                .content(request.getContent())
                .build();
        entry = timelineEntryRepository.save(entry);

        return TimelineResponse.builder()
                .id(entry.getId())
                .content(entry.getContent())
                .entryType(entry.getEntryType())
                .authorName(user.getName())
                .createdAt(entry.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<TimelineResponse> getTimelineForEvent(UUID eventId, User principal) {
        User user = reload(principal);
        Event event = findEvent(eventId);
        requireParticipant(event, user, "No tienes permisos para ver el contexto de este evento");

        return timelineEntryRepository.findByEventOrderByCreatedAtDesc(event).stream()
                .map(entry -> TimelineResponse.builder()
                        .id(entry.getId())
                        .content(entry.getContent())
                        .entryType(entry.getEntryType())
                        .authorName(entry.getAuthor().getName())
                        .createdAt(entry.getCreatedAt())
                        .build())
                .toList();
    }

    // =====================================================================================
    // Checklist
    // =====================================================================================

    @Transactional(readOnly = true)
    public List<ChecklistItemResponse> getChecklist(UUID eventId, User principal) {
        User user = reload(principal);
        Event event = findEvent(eventId);
        requireParticipant(event, user, "No tienes permisos para ver este evento");

        return checklistItemRepository.findAllByEvent(event).stream()
                .sorted(Comparator.comparing(ChecklistItem::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toChecklistResponse)
                .toList();
    }

    @Transactional
    public ChecklistItemResponse addChecklistItem(UUID eventId, ChecklistCreateRequest request, User principal) {
        User user = reload(principal);
        Event event = findEvent(eventId);
        requireParticipant(event, user, "No tienes permisos para modificar este evento");

        ChecklistItem item = checklistItemRepository.save(ChecklistItem.builder()
                .event(event)
                .description(request.getDescription().trim())
                .isCompleted(false)
                .build());
        return toChecklistResponse(item);
    }

    @Transactional
    public ChecklistItemResponse setChecklistItemCompleted(UUID eventId, UUID itemId, boolean completed,
                                                           User principal) {
        User user = reload(principal);
        Event event = findEvent(eventId);
        requireParticipant(event, user, "No tienes permisos para modificar este evento");

        ChecklistItem item = findChecklistItem(event, itemId);
        item.setIsCompleted(completed);
        item.setCompletedAt(completed ? LocalDateTime.now() : null);
        return toChecklistResponse(checklistItemRepository.save(item));
    }

    @Transactional
    public void deleteChecklistItem(UUID eventId, UUID itemId, User principal) {
        User user = reload(principal);
        Event event = findEvent(eventId);
        requireParticipant(event, user, "No tienes permisos para modificar este evento");

        checklistItemRepository.delete(findChecklistItem(event, itemId));
    }

    // =====================================================================================
    // Utilidades internas
    // =====================================================================================

    private User reload(User principal) {
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new UnauthorizedException("Usuario no encontrado"));
    }

    private Event findEvent(UUID eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento no encontrado"));
    }

    private void requireParticipant(Event event, User user, String message) {
        boolean isCreator = event.getCreatedBy().getId().equals(user.getId());
        boolean isAssigned = eventAssignmentRepository.findByEventAndUser(event, user).isPresent();
        if (!isCreator && !isAssigned) {
            throw new ForbiddenException(message);
        }
    }

    private ChecklistItem findChecklistItem(Event event, UUID itemId) {
        ChecklistItem item = checklistItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Tarea no encontrada"));
        if (!item.getEvent().getId().equals(event.getId())) {
            throw new ResourceNotFoundException("Tarea no encontrada");
        }
        return item;
    }

    private ChecklistItemResponse toChecklistResponse(ChecklistItem item) {
        return ChecklistItemResponse.builder()
                .id(item.getId())
                .description(item.getDescription())
                .completed(Boolean.TRUE.equals(item.getIsCompleted()))
                .completedAt(item.getCompletedAt())
                .build();
    }

    private void validateTimes(EventRequest request) {
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new BusinessException("La fecha de fin debe ser posterior a la fecha de inicio");
        }
    }

    private void assign(Event event, User user, boolean conflict) {
        eventAssignmentRepository.save(EventAssignment.builder()
                .event(event)
                .user(user)
                .hasConflict(conflict)
                .build());
    }

    private AssignmentResultResponse failure(UUID userId, String email, String error) {
        return AssignmentResultResponse.builder()
                .userId(userId)
                .userEmail(email)
                .hasConflict(false)
                .success(false)
                .error(error)
                .build();
    }

    private UUID resolveOrganizationId(EventAssignRequest request, Event event, User admin) {
        if (request.getOrganizationId() != null) {
            return request.getOrganizationId();
        }
        if (event.getOrganization() != null) {
            return event.getOrganization().getId();
        }
        List<Membership> adminMemberships = membershipRepository.findByUserAndLeftAtIsNull(admin).stream()
                .filter(m -> OrganizationService.ROLE_ADMIN.equals(m.getRole()))
                .toList();
        if (adminMemberships.size() == 1) {
            return adminMemberships.get(0).getOrganization().getId();
        }
        throw new BusinessException("Indica la organización desde la que asignas el evento (organizationId)");
    }

    // ---------------------------------------------------------------- recurrencia

    /** Valida y convierte la regla del request. null o "NONE" significa sin recurrencia. */
    private RecurrenceEngine.Spec toSpec(RecurrenceRequest recurrence) {
        if (recurrence == null || recurrence.getFrequency() == null || recurrence.getFrequency().isBlank()
                || "NONE".equalsIgnoreCase(recurrence.getFrequency().trim())) {
            return null;
        }
        return RecurrenceEngine.Spec.of(recurrence.getFrequency(), recurrence.getIntervalCount(),
                recurrence.getDaysOfWeek(), recurrence.getUntilDate());
    }

    /** Convierte la regla guardada en la regla del motor; datos antiguos o inválidos se tratan como sin recurrencia. */
    private RecurrenceEngine.Spec specOf(RecurrenceRule rule) {
        if (rule == null || rule.getFrequency() == null) {
            return null;
        }
        String frequency = rule.getFrequency().trim().toUpperCase(Locale.ROOT);
        if (!frequency.equals("DAILY") && !frequency.equals("WEEKLY") && !frequency.equals("MONTHLY")) {
            return null;
        }
        int interval = rule.getIntervalCount() == null || rule.getIntervalCount() < 1 ? 1 : rule.getIntervalCount();
        try {
            return new RecurrenceEngine.Spec(frequency, interval,
                    RecurrenceEngine.parseDays(rule.getDaysOfWeek()), rule.getUntilDate());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Crea, actualiza o elimina la regla del evento según spec. Devuelve la regla vigente (o null). */
    private RecurrenceRule saveRule(Event event, RecurrenceEngine.Spec spec) {
        Optional<RecurrenceRule> existing = recurrenceRuleRepository.findByEvent(event);
        if (spec == null) {
            existing.ifPresent(recurrenceRuleRepository::delete);
            return null;
        }
        RecurrenceRule rule = existing.orElseGet(() -> RecurrenceRule.builder().event(event).build());
        rule.setFrequency(spec.frequency());
        rule.setIntervalCount(spec.intervalCount());
        rule.setDaysOfWeek(RecurrenceEngine.joinDays(spec.daysOfWeek()));
        rule.setUntilDate(spec.untilDate());
        return recurrenceRuleRepository.save(rule);
    }

    private Map<UUID, RecurrenceRule> rulesByEvent(Collection<Event> events) {
        Map<UUID, RecurrenceRule> map = new HashMap<>();
        if (events.isEmpty()) {
            return map;
        }
        for (RecurrenceRule rule : recurrenceRuleRepository.findAllByEventIn(events)) {
            map.put(rule.getEvent().getId(), rule);
        }
        return map;
    }

    // ---------------------------------------------------------------- mapeo

    private EventResponse toResponse(Event event, RecurrenceRule rule, Boolean hasConflict, User viewer) {
        List<String> days = null;
        if (rule != null && rule.getDaysOfWeek() != null && !rule.getDaysOfWeek().isBlank()) {
            days = Arrays.asList(rule.getDaysOfWeek().split(","));
        }
        Organization org = event.getOrganization();
        return EventResponse.builder()
                .id(event.getId())
                .title(event.getTitle())
                .description(event.getDescription())
                .startTime(event.getStartTime())
                .endTime(event.getEndTime())
                .hasConflict(hasConflict != null && hasConflict)
                .recurrenceFrequency(rule == null ? null : rule.getFrequency())
                .intervalCount(rule == null ? null : rule.getIntervalCount())
                .daysOfWeek(days)
                .untilDate(rule == null ? null : rule.getUntilDate())
                .organizationId(org == null ? null : org.getId())
                .organizationName(org == null ? null : org.getName())
                .createdByName(event.getCreatedBy().getName())
                .owner(event.getCreatedBy().getId().equals(viewer.getId()))
                .build();
    }
}
