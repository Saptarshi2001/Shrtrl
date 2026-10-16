
package com.shrtrl.services;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.lettuce.core.RedisException;
import io.lettuce.core.api.sync.RedisCommands;

@Service
public class UrlCacheService
{
    private static final Logger logger = LoggerFactory.getLogger(UrlCacheService.class);

    private final RedisCommands<String,String> command;
    private final long ttl;
    public UrlCacheService(RedisCommands<String,String> command,@Value("${app.cache.url-ttl-seconds}") long ttl)
    {
        this.command=command;
        this.ttl=ttl;
    }

    public void set(String shortcode, String longurl)
    {
        try {
            command.setex("url:" + shortcode, ttl, longurl);
        } catch (RedisException exception) {
            logger.warn("Could not cache shortcode {}", shortcode, exception);
        }
    }

    public String get(String shortcode)
    {
        try {
            return command.get("url:" + shortcode);
        } catch (RedisException exception) {
            logger.warn("Could not read shortcode {} from cache", shortcode, exception);
            return null;
        }
    }

    public boolean exists(String shortcode)
    {
        try {
            return command.exists("url:" + shortcode) > 0;
        } catch (RedisException exception) {
            logger.warn("Could not check shortcode {} in cache", shortcode, exception);
            return false;
        }
    }

    public void delete(String shortcode)
    {
        try {
            command.del("url:" + shortcode);
        } catch (RedisException exception) {
            logger.warn("Could not delete shortcode {} from cache", shortcode, exception);
        }
    }    

}
