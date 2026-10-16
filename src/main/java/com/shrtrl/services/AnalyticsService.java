package com.shrtrl.services;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.insert.InsertResponse;
import com.clickhouse.client.api.query.GenericRecord;
import com.clickhouse.data.ClickHouseFormat;
import com.shrtrl.models.Event;

import jakarta.annotation.PostConstruct;

@Service
public class AnalyticsService
{
    private final Client clickevent;

    public AnalyticsService(Client clickevent)
    {
        this.clickevent=clickevent;
    }

    @PostConstruct
    void registerEventType()
    {
        clickevent.register(Event.class, clickevent.getTableSchema("events"));
    }

    public void insertData(String table,InputStream data)
    {
        clickevent.insert(table,data,ClickHouseFormat.JSONAsObject);
    }

    public void insertEvents(List<Event> events)
    {
        try (InsertResponse ignored = clickevent.insert("events", events).get())
        {
            // Closing the response releases the connection back to the client pool.
        }
        catch (InterruptedException exception)
        {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ClickHouse insertion was interrupted", exception);
        }
        catch (Exception exception)
        {
            throw new IllegalStateException("ClickHouse insertion failed", exception);
        }
    }

    public List<GenericRecord> clicksByCountry(UUID userId)
    {
        return queryForUser("""
                SELECT country, shortcode, count(shortcode) AS cnt
                FROM events
                WHERE userid = {userId:UUID}
                GROUP BY country, shortcode
                ORDER BY country DESC
                """, userId);
    }

    public List<GenericRecord> topFiveShortCodes(UUID userId)
    {
        return queryForUser("""
                SELECT shortcode, count(shortcode) AS cnt
                FROM events
                WHERE userid = {userId:UUID}
                GROUP BY shortcode
                ORDER BY cnt DESC, shortcode
                LIMIT 5
                """, userId);
    }

    public List<GenericRecord> eventsInLastHour(UUID userId)
    {
        return queryForUser("""
                SELECT shortcode, country, state, ip, referrer
                FROM events
                WHERE userid = {userId:UUID}
                  AND occurred_at >= now() - INTERVAL 1 HOUR
                ORDER BY occurred_at DESC
                """, userId);
    }

    public List<GenericRecord> hourlyClicksInLast24Hours(UUID userId)
    {
        return queryForUser("""
                SELECT shortcode, toStartOfHour(occurred_at) AS hour,
                       count(shortcode) AS cnt
                FROM events
                WHERE userid = {userId:UUID}
                  AND occurred_at >= now() - INTERVAL 24 HOUR
                GROUP BY shortcode, hour
                ORDER BY hour DESC, cnt DESC
                """, userId);
    }

    public List<GenericRecord> clicksToday(UUID userId)
    {
        return queryForUser("""
                SELECT userid, shortcode, count(shortcode) AS cnt, country, state
                FROM events
                WHERE userid = {userId:UUID}
                  AND occurred_at >= today()
                  AND occurred_at < today() + INTERVAL 1 DAY
                GROUP BY userid, shortcode, country, state
                ORDER BY cnt DESC
                """, userId);
    }

    public List<GenericRecord> clicksThisMonth(UUID userId)
    {
        return queryForUser("""
                SELECT userid, shortcode, count(shortcode) AS cnt, country, state
                FROM events
                WHERE userid = {userId:UUID}
                  AND occurred_at >= toStartOfMonth(today())
                  AND occurred_at < addMonths(toStartOfMonth(today()), 1)
                GROUP BY userid, shortcode, country, state
                ORDER BY cnt DESC
                """, userId);
    }

    public List<GenericRecord> clicksThisYear(UUID userId)
    {
        return queryForUser("""
                SELECT userid, shortcode, count(shortcode) AS cnt, country, state
                FROM events
                WHERE userid = {userId:UUID}
                  AND toYear(occurred_at) = toYear(today())
                GROUP BY userid, shortcode, country, state
                ORDER BY cnt DESC
                """, userId);
    }

    public List<GenericRecord> clicksByReferrer(UUID userId)
    {
        return queryForUser("""
                SELECT userid, shortcode, referrer, count(*) AS cnt
                FROM events
                WHERE userid = {userId:UUID}
                GROUP BY userid, shortcode, referrer
                ORDER BY cnt DESC
                """, userId);
    }

    public List<GenericRecord> clicksByCountryAndState(UUID userId)
    {
        return queryForUser("""
                SELECT userid, shortcode, count(shortcode) AS cnt, country, state
                FROM events
                WHERE userid = {userId:UUID}
                GROUP BY userid, shortcode, country, state
                ORDER BY cnt DESC
                """, userId);
    }

    private List<GenericRecord> queryForUser(String sql, UUID userId)
    {
        Objects.requireNonNull(userId, "userId is required");
        return query(sql, Map.of("userId", userId));
    }

    public List<GenericRecord> query(String sql,Map<String,Object> parameters)
    {

        return clickevent.queryAll(sql, parameters);
    }



}
