package com.shrtrl.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shrtrl.transport.EventStorage;
import com.shrtrl.transport.Postgres.OutboxRepository;
import com.shrtrl.transport.Postgres.PostgresEventInsertion;
import com.shrtrl.transport.Redis.RedisEventInsertion;

import io.lettuce.core.api.sync.RedisCommands;

class AnalyticsTransportSelectionIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TransportComponents.class)
            .withBean(OutboxRepository.class, () -> mock(OutboxRepository.class))
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(RedisCommands.class, () -> mock(RedisCommands.class));

    @Test
    void postgresPropertySelectsOnlyThePostgresTransport() {
        contextRunner
                .withPropertyValues("app.analytics.transport=postgres")
                .run(context -> {
                    assertThat(context).hasSingleBean(EventStorage.class);
                    assertThat(context.getBean(EventStorage.class))
                            .isInstanceOf(PostgresEventInsertion.class);
                    assertThat(context).doesNotHaveBean(RedisEventInsertion.class);
                });
    }

    @Test
    void redisPropertySelectsOnlyTheRedisTransport() {
        contextRunner
                .withPropertyValues("app.analytics.transport=redis")
                .run(context -> {
                    assertThat(context).hasSingleBean(EventStorage.class);
                    assertThat(context.getBean(EventStorage.class))
                            .isInstanceOf(RedisEventInsertion.class);
                    assertThat(context).doesNotHaveBean(PostgresEventInsertion.class);
                });
    }

    @Test
    void missingPropertyDoesNotSilentlyChooseATransport() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(EventStorage.class));
    }

    @Configuration(proxyBeanMethods = false)
    @Import({ PostgresEventInsertion.class, RedisEventInsertion.class })
    static class TransportComponents {
    }
}
