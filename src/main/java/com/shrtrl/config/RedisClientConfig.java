package com.shrtrl.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
@Configuration
public class RedisClientConfig {

    @Bean(destroyMethod = "close")
    public RedisClient redisClient(@Value("${app.redis.uri}") String redisUri) {
        return RedisClient.create(redisUri);
    }
    
    @Bean(destroyMethod="close")
    public StatefulRedisConnection<String,String> redisConnection(RedisClient redisClient)
    {
    	return redisClient.connect();
    }

    @Bean
    public RedisCommands<String,String> getCommands(StatefulRedisConnection<String,String> connection)
    {
        RedisCommands<String,String>command=connection.sync();
        return command;
    }
}
