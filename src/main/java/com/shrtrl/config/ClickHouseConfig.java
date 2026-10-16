package com.shrtrl.config;

import com.clickhouse.client.api.Client;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClickHouseConfig {

    @Bean(destroyMethod = "close")
    public Client clickHouseClient(
            @Value("${clickhouse.endpoint}") String endpoint,
            @Value("${clickhouse.username}") String username,
            @Value("${clickhouse.password}") String password,
            @Value("${clickhouse.database}") String database) {
        return new Client.Builder()
                .addEndpoint(endpoint)
                .setUsername(username)
                .setPassword(password)
                .setDefaultDatabase(database)
                .setClientName("shrtrl")
                .build();
    }
}
