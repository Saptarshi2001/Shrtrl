
package com.shrtrl.analytics.Transport.Postgres;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.shrtrl.models.Event;

public interface OutboxRepository extends JpaRepository<Event, UUID> {

    List<Event> findAllByOrderByOccurredAtAsc(Pageable pageable);
}
