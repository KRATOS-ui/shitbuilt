package com.example.pasaas2.repository;

import com.example.pasaas2.model.Event;
import com.example.pasaas2.model.Organization;
import com.example.pasaas2.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    List<Event> findByOrganizationOrderByStartTimeAsc(Organization organization);

    List<Event> findByCreatedBy(User createdBy);
}
