
package com.shrtrl.transport.Postgres;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.shrtrl.transport.EventStorage;
import com.shrtrl.models.Event;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(
    name="app.analytics.transport",
    havingValue="postgres"
)
public class PostgresEventInsertion implements EventStorage
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresEventInsertion.class);

    private final OutboxRepository outbox;
    public PostgresEventInsertion(OutboxRepository outbox)
    {
        this.outbox=outbox;
    }

    @Override
    @Transactional
    public void insertEvent(Event event)
    {
        try {
            outbox.saveAndFlush(event);
        } catch (RuntimeException exception) {
            LOGGER.error("Could not save analytics event to PostgreSQL outbox", exception);
            throw exception;
        }
    }
}
