package com.shrtrl.transport.Postgres;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.shrtrl.models.Event;
import com.shrtrl.services.AnalyticsService;

@Component
@ConditionalOnProperty(
        name = "app.analytics.transport",
        havingValue = "postgres"
)
public class OutboxWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxWorker.class);

    private static final int BATCH_SIZE = 10_000;

    private final OutboxRepository outbox;
    private final AnalyticsService analyticsService;

    public OutboxWorker(
            OutboxRepository outbox,
            AnalyticsService analyticsService) {
        this.outbox = outbox;
        this.analyticsService = analyticsService;
    }

    @Transactional
    @Scheduled(
            fixedDelayString = "${app.analytics.outbox.poll-delay-ms:5000}",
            initialDelayString = "${app.analytics.outbox.initial-delay-ms:5000}"
    )
    public int insertToClickHouse() {
        try {
            // Hold the row locks until delivery and deletion commit together in PostgreSQL.
            List<Event> events = outbox.lockNextBatch(BATCH_SIZE);

            if (events.isEmpty()) {
                return 0;
            }

            analyticsService.insertEvents(events);
            // A failed insertion throws before deletion, leaving this batch for the next poll.
            outbox.deleteAllInBatch(events);
            return 1;
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to deliver PostgreSQL outbox events to ClickHouse; transaction will roll back", exception);
            throw exception;
        }
    }
}
