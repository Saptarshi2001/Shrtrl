package com.shrtrl.DTOs;

import java.time.Instant;

public record HourlyClicksResponse(String shortcode, Instant hour, long clicks) {}
