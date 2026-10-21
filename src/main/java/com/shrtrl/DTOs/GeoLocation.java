package com.shrtrl.DTOs;

public record GeoLocation(String country, String state) {
    public static final GeoLocation UNKNOWN = new GeoLocation(null, null);
}
