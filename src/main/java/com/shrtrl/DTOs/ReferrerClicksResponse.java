package com.shrtrl.DTOs;

import java.util.UUID;

public record ReferrerClicksResponse(UUID userId, String shortcode, String referrer, long clicks) {}
