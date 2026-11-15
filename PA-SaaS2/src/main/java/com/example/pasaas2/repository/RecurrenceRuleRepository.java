package com.example.pasaas2.repository;

import com.example.pasaas2.model.Event;
import com.example.pasaas2.model.RecurrenceRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecurrenceRuleRepository extends JpaRepository<RecurrenceRule, UUID> {

    void deleteByEvent(Event event);

    Optional<RecurrenceRule> findByEvent(Event event);

    List<RecurrenceRule> findAllByEventIn(Collection<Event> events);
}
