package com.example.pasaas2.repository;

import com.example.pasaas2.model.Event;
import com.example.pasaas2.model.EventAssignment;
import com.example.pasaas2.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventAssignmentRepository extends JpaRepository<EventAssignment, UUID> {

    @Query("SELECT COUNT(ea) > 0 FROM EventAssignment ea JOIN ea.event e " +
            "WHERE ea.user = :user AND " +
            "(e.startTime < :endTime AND e.endTime > :startTime)")
    boolean hasOverlappingEvents(@Param("user") User user,
                                 @Param("startTime") LocalDateTime startTime,
                                 @Param("endTime") LocalDateTime endTime);

    // Igual que la anterior pero ignorando un evento (el que se está editando no choca consigo mismo)
    @Query("SELECT COUNT(ea) > 0 FROM EventAssignment ea JOIN ea.event e " +
            "WHERE ea.user = :user AND e.id <> :excludedEventId AND " +
            "(e.startTime < :endTime AND e.endTime > :startTime)")
    boolean hasOverlappingEventsExcluding(@Param("user") User user,
                                          @Param("startTime") LocalDateTime startTime,
                                          @Param("endTime") LocalDateTime endTime,
                                          @Param("excludedEventId") UUID excludedEventId);

    void deleteByEvent(Event event);

    void deleteByUser(User user);

    List<EventAssignment> findAllByUser(User currentUser);

    List<EventAssignment> findAllByEvent(Event event);

    List<EventAssignment> findAllByEventIn(Collection<Event> events);

    Optional<EventAssignment> findByEventAndUser(Event event, User user);
}
