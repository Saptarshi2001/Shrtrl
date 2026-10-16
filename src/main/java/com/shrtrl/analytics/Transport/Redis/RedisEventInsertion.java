package com.shrtrl.analytics.Transport.Redis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import jakarta.transaction.Transactional;
import com.shrtrl.analytics.Transport.EventStorage;
import com.shrtrl.models.Event;
import io.lettuce.core.api.sync.RedisCommands;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import com.fasterxml.jackson.core.JsonProcessingException;
@Component
@ConditionalOnProperty(
    name="app.analytics.transport",
    havingValue="redis"
)
public class RedisEventInsertion implements EventStorage
{

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
    
    } catch (JsonProcessingException exception) {
        throw new IllegalStateException("Could not serialize analytics event",exception);
    }
    }
    
}
