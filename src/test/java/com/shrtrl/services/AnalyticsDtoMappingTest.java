package com.shrtrl.services;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.query.GenericRecord;
import com.shrtrl.DTOs.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AnalyticsDtoMappingTest {
    @Test
    void mapsQueryColumnsIntoTypedResponsesAndJson() {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant timestamp = Instant.parse("2026-10-25T10:00:00Z");
        Client client = mock(Client.class);
        GenericRecord row = mock(GenericRecord.class);
        when(client.queryAll(anyString(), eq(Map.of("userId", userId))))
                .thenReturn(List.of(row));
        when(row.getUUID("userid")).thenReturn(userId);
        when(row.getUUID("event_id")).thenReturn(eventId);
        when(row.getString("shortcode")).thenReturn("abc123");
        when(row.getString("country")).thenReturn("India");
        when(row.getString("state")).thenReturn("West Bengal");
        when(row.getString("ip")).thenReturn("127.0.0.1");
        when(row.getString("user_agent")).thenReturn("test-agent");
        when(row.getString("referrer")).thenReturn(null);
        when(row.getLong("cnt")).thenReturn(3_000_000_000L);
        when(row.getInstant("occurred_at")).thenReturn(timestamp);
        when(row.getInstant("hour")).thenReturn(timestamp);
        AnalyticsService service = new AnalyticsService(client);

        ClickSummaryResponse summary = new ClickSummaryResponse(
                "abc123", 3_000_000_000L, "India", "West Bengal");
        assertEquals(List.of(summary), service.clicksToday(userId));
        assertEquals(List.of(summary), service.clicksThisMonth(userId));
        assertEquals(List.of(summary), service.clicksThisYear(userId));
        assertEquals(List.of(summary), service.clicksByCountryAndState(userId));
        assertEquals(new CountryClicksResponse("India", "abc123", 3_000_000_000L),
                service.clicksByCountry(userId).getFirst());
        assertEquals(new ShortCodeClicksResponse("abc123", 3_000_000_000L),
                service.topFiveShortCodes(userId).getFirst());
        assertEquals(new LiveAnalyticsResponse("abc123", "India", "West Bengal", "127.0.0.1", null),
                service.eventsInLastHour(userId).getFirst());
        assertEquals(new HourlyClicksResponse("abc123", timestamp, 3_000_000_000L),
                service.hourlyClicksInLast24Hours(userId).getFirst());
        assertEquals(new ReferrerClicksResponse(userId, "abc123", null, 3_000_000_000L),
                service.clicksByReferrer(userId).getFirst());
        AnalyticsEventResponse event = service.showanalytics(userId).getFirst();
        assertEquals(new AnalyticsEventResponse(eventId, userId, "abc123", timestamp,
                "India", "West Bengal", "127.0.0.1", "test-agent", null), event);

        ObjectMapper mapper = new ObjectMapper();
        var json = mapper.readTree(mapper.writeValueAsString(List.of(summary)));
        assertEquals(3_000_000_000L, json.get(0).get("clicks").asLong());
        assertEquals("abc123", json.get(0).get("shortcode").asString());
        assertFalse(json.get(0).has("cnt"));
        assertTrue(mapper.writeValueAsString(event).contains(timestamp.toString()));
    }

    @Test
    void emptyQueryReturnsEmptyDtoList() {
        Client client = mock(Client.class);
        UUID userId = UUID.randomUUID();
        when(client.queryAll(anyString(), eq(Map.of("userId", userId))))
                .thenReturn(List.of());
        assertTrue(new AnalyticsService(client).clicksToday(userId).isEmpty());
    }
}
