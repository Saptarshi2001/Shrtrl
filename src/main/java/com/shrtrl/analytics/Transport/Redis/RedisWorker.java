package com.shrtrl.analytics.Transport.Redis;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.shrtrl.services.AnalyticsService;

import io.lettuce.core.Consumer;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.XReadArgs.StreamOffset;
import io.lettuce.core.XReadArgs;
import io.lettuce.core.api.sync.RedisCommands;

import java.util.ArrayList;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shrtrl.models.Event;

@Component
@ConditionalOnProperty(
        name = "app.analytics.transport",
        havingValue = "redis"
)
public class RedisWorker
{

    private static final String STREAM = "analytics:clicks";
    private static final String GROUP = "clickhouse-workers";
    private static final String CONSUMER = "worker-1";
    private static final int BATCH_SIZE = 10_000;
    private final ObjectMapper objectMapper;
    private final RedisCommands<String,String>cmd;
    private final AnalyticsService analyticsClient;
    public RedisWorker(RedisCommands<String,String>cmd,AnalyticsService analyticsClient,ObjectMapper objectMapper)
    {
        this.cmd=cmd;
        this.analyticsClient=analyticsClient;
        this.objectMapper=objectMapper;
    }
    
    @Scheduled(fixedRate=5000)
    public int insertToClickhouse()
    {
        try{
     List<Event> events = new ArrayList<>();   

     List<StreamMessage<String, String>> messages =cmd.xreadgroup(Consumer.from(GROUP,CONSUMER),XReadArgs.Builder.count(BATCH_SIZE).block(Duration.ofSeconds(1)),StreamOffset.lastConsumed(STREAM));
                        
           for (StreamMessage<String, String> message : messages) {
        String payload = message.getBody().get("payload");

        
            Event event = objectMapper.readValue(payload,Event.class);
            events.add(event);
        
           }

            
                // This must return only after ClickHouse confirms success.
                analyticsClient.insertEvents(events);

                String[] messageIds = messages.stream()
                        .map(StreamMessage::getId)
                        .toArray(String[]::new);

                cmd.xack(STREAM, GROUP, messageIds);
                return 1;
            } catch (Exception exception) {

                return 0;
                // Do not XACK.
                // Messages remain pending in Redis for retry/recovery.
            }
    }



}
