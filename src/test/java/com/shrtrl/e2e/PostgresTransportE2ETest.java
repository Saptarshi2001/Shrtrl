package com.shrtrl.e2e;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import com.clickhouse.client.api.Client;
import com.shrtrl.transport.EventStorage;
import com.shrtrl.transport.Postgres.OutboxRepository;
import com.shrtrl.transport.Postgres.OutboxWorker;
import com.shrtrl.transport.Postgres.PostgresEventInsertion;
import com.shrtrl.models.Event;

import static org.junit.jupiter.api.Assertions.*;

@TestPropertySource(properties = "app.analytics.outbox.initial-delay-ms=600000")
class PostgresTransportE2ETest extends AbstractE2ETest {
    @Autowired EventStorage storage;
    @Autowired OutboxRepository outbox;
    @Autowired OutboxWorker worker;
    @Autowired Client clickhouse;

    @Test
    void deliversCompleteEventFromPostgresToRealClickHouse() {
        var event = event("abc123");
        assertInstanceOf(PostgresEventInsertion.class, storage);
        storage.insertEvent(event);
        assertTrue(outbox.existsById(event.getEventId()));

        assertEquals(1, worker.insertToClickHouse());
        assertFalse(outbox.existsById(event.getEventId()));
        var rows = clickhouse.queryAll("SELECT * FROM events WHERE event_id = {id:UUID}",
                java.util.Map.of("id", event.getEventId()));
        assertEquals(1, rows.size());
        assertEquals("India", rows.getFirst().getString("country"));
        assertEquals("West Bengal", rows.getFirst().getString("state"));
        assertEquals(event.getReferrer(), rows.getFirst().getString("referrer"));
    }

    @Test
    void deliversEventsWhenOptionalVisitorFieldsAreUnknown() {
        var event = new Event(UUID.randomUUID(), UUID.randomUUID(), "local1", Instant.now(),
                null, null, "127.0.0.1", null, null);
        storage.insertEvent(event);
        assertEquals(1, worker.insertToClickHouse());
        assertFalse(outbox.existsById(event.getEventId()));
        assertEquals(1, clickhouse.queryAll("SELECT event_id FROM events WHERE event_id = {id:UUID}",
                java.util.Map.of("id", event.getEventId())).size());
    }

    @Test
    void retainsFailedDeliveryAndRetriesAfterClickHouseRecovers() throws Exception {
        var event = event("retry1");
        storage.insertEvent(event);
        try (var ignored = clickhouse.query("RENAME TABLE events TO events_unavailable").get()) {
            // Simulate an unavailable destination using only the disposable test database.
        }
        try {
            assertThrows(IllegalStateException.class, worker::insertToClickHouse);
            assertTrue(outbox.existsById(event.getEventId()));
        } finally {
            try (var ignored = clickhouse.query("RENAME TABLE events_unavailable TO events").get()) {
                // Restore the test destination even if an assertion fails.
            }
        }
        assertEquals(1, worker.insertToClickHouse());
        assertFalse(outbox.existsById(event.getEventId()));
        assertEquals(1, clickhouse.queryAll("SELECT event_id FROM events WHERE event_id = {id:UUID}",
                java.util.Map.of("id", event.getEventId())).size());
    }

    @Test
    void anotherWorkerSkipsLockedEventsAndCanDeliverAfterRelease() throws Exception {
        var event = event("lock1");
        storage.insertEvent(event);
        var dataSource = jdbc.getDataSource();
        assertNotNull(dataSource);
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement(
                    "SELECT event_id FROM analytics_outbox WHERE event_id = ? FOR UPDATE")) {
                statement.setObject(1, event.getEventId());
                try (var rows = statement.executeQuery()) {
                    assertTrue(rows.next());
                }
                assertEquals(0, worker.insertToClickHouse());
                assertTrue(outbox.existsById(event.getEventId()));
            } finally {
                connection.rollback();
            }
        }
        assertEquals(1, worker.insertToClickHouse());
        assertFalse(outbox.existsById(event.getEventId()));
    }

    private Event event(String code) {
        return new Event(UUID.randomUUID(), UUID.randomUUID(), code, Instant.now(),
                "India", "West Bengal", "203.0.113.1", "transport-test",
                "https://example.com/a-normal-length-referrer");
    }
}
