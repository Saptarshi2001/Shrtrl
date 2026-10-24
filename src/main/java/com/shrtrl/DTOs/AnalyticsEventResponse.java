package com.shrtrl.DTOs;

import java.util.UUID;
import java.time.Instant;

public record AnalyticsEventResponse(UUID eventId, UUID userId, String shortcode, Instant occurredAt, String country, String state, String ip, String userAgent, String referrer) {}
