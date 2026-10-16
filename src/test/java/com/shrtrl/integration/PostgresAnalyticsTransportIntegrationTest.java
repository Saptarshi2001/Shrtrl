package com.shrtrl.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.shrtrl.analytics.Transport.EventStorage;
import com.shrtrl.analytics.Transport.Postgres.OutboxRepository;
import com.shrtrl.analytics.Transport.Postgres.OutboxWorker;
import com.shrtrl.analytics.Transport.Postgres.PostgresEventInsertion;
import com.shrtrl.models.Event;
import com.shrtrl.services.AnalyticsService;

import jakarta.persistence.EntityManager;

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(properties = {
        "app.analytics.transport=postgres",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ PostgresEventInsertion.class, OutboxWorker.class })
class PostgresAnalyticsTransportIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("shrtrl_transport_test")
            .withUsername("shrtrl")
            .withPassword("shrtrl_test_password");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private EventStorage eventStorage;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private OutboxWorker outboxWorker;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private AnalyticsService analyticsService;

    @Test
    void postgresTransportStoresTheCompleteEventInTheOutbox() {
        Event event = eventAt("2026-09-06T08:30:00Z", "abc123");

        eventStorage.insertEvent(event);
        outboxRepository.flush();
        entityManager.clear();

        Event stored = outboxRepository.findById(event.getEventId()).orElseThrow();
        assertThat(eventStorage).isInstanceOf(PostgresEventInsertion.class);
        assertThat(stored.getEventId()).isEqualTo(event.getEventId());
        assertThat(stored.getUserId()).isEqualTo(event.getUserId());
        assertThat(stored.getShortCode()).isEqualTo("abc123");
        assertThat(stored.getOccurredAt()).isEqualTo(event.getOccurredAt());
        assertThat(stored.getCountry()).isEqualTo("India");
        assertThat(stored.getState()).isEqualTo("West Bengal");
        assertThat(stored.getIp()).isEqualTo("203.0.113.10");
        assertThat(stored.getUserAgent()).isEqualTo("integration-test-agent");
        assertThat(stored.getReferrer()).isEqualTo("https://example.test/source");
    }

    @Test
    void repositoryReturnsTheOldestEventsFirstAndHonoursThePageSize() {
        Event newest = eventAt("2026-09-06T10:00:00Z", "newest");
        Event oldest = eventAt("2026-09-06T08:00:00Z", "oldest");
        Event middle = eventAt("2026-09-06T09:00:00Z", "middle");
        outboxRepository.saveAllAndFlush(List.of(newest, oldest, middle));

        List<Event> batch = outboxRepository.findAllByOrderByOccurredAtAsc(
                PageRequest.of(0, 2)
        );

        assertThat(batch)
                .extracting(Event::getEventId)
                .containsExactly(oldest.getEventId(), middle.getEventId());
    }

    @Test
    void workerWritesAClaimedBatchThenDeletesItFromPostgres() {
        Event first = eventAt("2026-09-06T08:00:00Z", "first");
        Event second = eventAt("2026-09-06T09:00:00Z", "second");
        outboxRepository.saveAllAndFlush(List.of(second, first));

        int result = outboxWorker.insertToClickHouse();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Event>> eventsCaptor = ArgumentCaptor.forClass(List.class);
        verify(analyticsService).insertEvents(eventsCaptor.capture());
        assertThat(eventsCaptor.getValue())
                .extracting(Event::getEventId)
                .containsExactly(first.getEventId(), second.getEventId());
        assertThat(result).isEqualTo(1);
        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    void workerKeepsTheBatchWhenClickHouseInsertionFails() {
        Event event = eventAt("2026-09-06T08:00:00Z", "retry-me");
        outboxRepository.saveAndFlush(event);
        doThrow(new IllegalStateException("ClickHouse unavailable"))
                .when(analyticsService).insertEvents(anyList());

        assertThatThrownBy(outboxWorker::insertToClickHouse)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ClickHouse unavailable");
        assertThat(outboxRepository.findById(event.getEventId())).isPresent();
    }

    @Test
    void workerDoesNothingWhenTheOutboxIsEmpty() {
        int result = outboxWorker.insertToClickHouse();

        assertThat(result).isZero();
        assertThat(outboxRepository.count()).isZero();
    }

    private static Event eventAt(String occurredAt, String shortCode) {
        return new Event(
                UUID.randomUUID(),
                UUID.randomUUID(),
                shortCode,
                Instant.parse(occurredAt),
                "India",
                "West Bengal",
                "203.0.113.10",
                "integration-test-agent",
                "https://example.test/source"
        );
    }
}
