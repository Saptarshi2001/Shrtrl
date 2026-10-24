package com.shrtrl.transport.Redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import jakarta.transaction.Transactional;
import com.shrtrl.transport.EventStorage;
import com.shrtrl.models.Event;
import io.lettuce.core.api.sync.RedisCommands;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;
import tools.jackson.core.JacksonException;
@Component
@ConditionalOnProperty(
    name="app.analytics.transport",
    havingValue="redis"
)
public class RedisEventInsertion implements EventStorage
{
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisEventInsertion.class);

    private RedisCommands<String,String> command;
    private final ObjectMapper objectMapper;
    public RedisEventInsertion(RedisCommands<String, String> command,ObjectMapper objectMapper) {
    this.command = command;
    this.objectMapper = objectMapper;
}
    
    public void insertEvent(Event event)
    {
        try {

        String eventJson = objectMapper.writeValueAsString(event);
        command.xadd("analytics:clicks",Map.of("payload", eventJson));
    
    } catch (JacksonException exception) {
        LOGGER.error("Could not serialize analytics event for Redis", exception);
        throw new IllegalStateException("Could not serialize analytics event",exception);
    } catch (RuntimeException exception) {
        LOGGER.error("Could not enqueue analytics event in Redis", exception);
        throw exception;
    }
    }
    
}
