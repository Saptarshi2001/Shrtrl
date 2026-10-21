
package com.shrtrl.transport.Postgres;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shrtrl.models.Event;

public interface OutboxRepository extends JpaRepository<Event, UUID> {


    @Query(value = """
            SELECT * FROM analytics_outbox
            ORDER BY occurred_at, event_id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Event> lockNextBatch(@Param("batchSize") int batchSize);
}
