package com.example.pasaas2.repository;

import com.example.pasaas2.model.Event;
import com.example.pasaas2.model.TimelineEntry;
import com.example.pasaas2.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TimelineEntryRepository extends JpaRepository<TimelineEntry, UUID> {

    List<TimelineEntry> findByEventOrderByCreatedAtDesc(Event event);

    void deleteByAuthor(User author);
}
