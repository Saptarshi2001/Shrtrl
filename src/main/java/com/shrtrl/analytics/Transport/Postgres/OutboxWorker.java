package com.shrtrl.analytics.Transport.Postgres;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
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
            fixedRate = 5000
    )
    public int insertToClickHouse() {
        List<Event> events = outbox.findAllByOrderByOccurredAtAsc(
                PageRequest.of(0, BATCH_SIZE)
        );

        if (events.isEmpty()) {
            return 0;
        }

        analyticsService.insertEvents(events);
        outbox.deleteAllInBatch(events);
        return 1;
    }
}
