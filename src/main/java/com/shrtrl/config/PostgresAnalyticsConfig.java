package com.shrtrl.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.analytics.transport", havingValue = "postgres")
@EnableScheduling
public class PostgresAnalyticsConfig {
}
