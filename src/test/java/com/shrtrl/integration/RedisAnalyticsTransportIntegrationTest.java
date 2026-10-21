package com.shrtrl.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.shrtrl.transport.Redis.RedisEventInsertion;
import com.shrtrl.transport.Redis.RedisWorker;
import com.shrtrl.models.Event;
import com.shrtrl.services.AnalyticsService;

import io.lettuce.core.Range;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.XGroupCreateArgs;
import io.lettuce.core.XReadArgs.StreamOffset;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

@Testcontainers(disabledWithoutDocker = true)
class RedisAnalyticsTransportIntegrationTest {

    private static final String STREAM = "analytics:clicks";
    private static final String GROUP = "clickhouse-workers";

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    private RedisClient redisClient;
    private StatefulRedisConnection<String, String> connection;
    private RedisCommands<String, String> commands;
    private ObjectMapper objectMapper;

    @BeforeEach
    void connectToRedis() {
        RedisURI redisUri = RedisURI.Builder.redis(
                redis.getHost(),
                redis.getMappedPort(6379)
        ).build();
        redisClient = RedisClient.create(redisUri);
        connection = redisClient.connect();
        commands = connection.sync();
        commands.flushall();
        objectMapper = tools.jackson.databind.json.JsonMapper.builder().build();
    }

    @AfterEach
    void disconnectFromRedis() {
        connection.close();
        redisClient.shutdown();
    }

    @Test
    void redisTransportAppendsOneJsonPayloadToTheAnalyticsStream() throws Exception {
        Event event = eventAt("2026-09-06T08:30:00Z", "abc123");
        RedisEventInsertion transport = new RedisEventInsertion(commands, objectMapper);

        transport.insertEvent(event);

        List<StreamMessage<String, String>> messages = commands.xrange(
                STREAM,
                Range.unbounded()
        );
        assertThat(messages).hasSize(1);
        assertThat(messages.getFirst().getBody()).containsOnlyKeys("payload");

        JsonNode payload = objectMapper.readTree(messages.getFirst().getBody().get("payload"));
        assertThat(payload.get("eventId").asText()).isEqualTo(event.getEventId().toString());
        assertThat(payload.get("userId").asText()).isEqualTo(event.getUserId().toString());
        assertThat(payload.get("shortCode").asText()).isEqualTo("abc123");
        assertThat(payload.get("occurredAt").asText()).isEqualTo("2026-09-06T08:30:00Z");
        assertThat(payload.get("country").asText()).isEqualTo("India");
    }

    @Test
    void workerInsertsTheStreamMessageAndAcknowledgesItOnlyAfterSuccess() {
        createConsumerGroup();
        Event event = eventAt("2026-09-06T08:30:00Z", "success");
        new RedisEventInsertion(commands, objectMapper).insertEvent(event);
        AnalyticsService analyticsService = mock(AnalyticsService.class);
        RedisWorker worker = new RedisWorker(commands, analyticsService, objectMapper);

        int result = worker.insertToClickhouse();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Event>> eventsCaptor = ArgumentCaptor.forClass(List.class);
        verify(analyticsService).insertEvents(eventsCaptor.capture());
        assertThat(eventsCaptor.getValue()).singleElement().satisfies(received -> {
            assertThat(received.getEventId()).isEqualTo(event.getEventId());
            assertThat(received.getShortCode()).isEqualTo("success");
        });
        assertThat(result).isEqualTo(1);
        assertThat(commands.xpending(STREAM, GROUP).getCount()).isZero();
    }

    @Test
    void workerLeavesTheMessagePendingWhenClickHouseInsertionFails() {
        createConsumerGroup();
        Event event = eventAt("2026-09-06T08:30:00Z", "retry-me");
        new RedisEventInsertion(commands, objectMapper).insertEvent(event);
        AnalyticsService analyticsService = mock(AnalyticsService.class);
        doThrow(new IllegalStateException("ClickHouse unavailable"))
                .when(analyticsService).insertEvents(anyList());
        RedisWorker worker = new RedisWorker(commands, analyticsService, objectMapper);

        int result = worker.insertToClickhouse();

        assertThat(result).isZero();
        assertThat(commands.xpending(STREAM, GROUP).getCount()).isEqualTo(1);
    }

    private void createConsumerGroup() {
        commands.xgroupCreate(
                StreamOffset.from(STREAM, "0-0"),
                GROUP,
                XGroupCreateArgs.Builder.mkstream()
        );
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
