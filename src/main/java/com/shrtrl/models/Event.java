
package com.shrtrl.models;

import java.time.Instant;
import java.util.UUID;
import org.hibernate.validator.constraints.Length;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "analytics_outbox")
public class Event {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "userid", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "shortcode", nullable = false, updatable = true)
    @Length(max=6)
    private String shortCode;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "country", length = 100, updatable = false)
    private String country;

    @Column(name = "state", length = 100, updatable = false)
    private String state;

    @Column(name = "ip", length = 45, updatable = false)
    private String ip;

    @Column(name = "user_agent", columnDefinition = "TEXT", updatable = false)
    private String userAgent;

    @Column(name = "referrer", columnDefinition = "TEXT", updatable = false)
    private String referrer;

    protected Event() {
        // Required by JPA.
    }

    public Event(
            UUID eventId,
            UUID userId,
            String shortCode,
            Instant occurredAt,
            String country,
            String state,
            String ip,
            String userAgent,
            String referrer) {
        this.eventId = eventId;
        this.userId = userId;
        this.shortCode = shortCode;
        this.occurredAt = occurredAt;
        this.country = country;
        this.state = state;
        this.ip = ip;
        this.userAgent = userAgent;
        this.referrer = referrer;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getShortCode() {
        return shortCode;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getCountry() {
        return country;
    }

    public String getState() {
        return state;
    }

    public String getIp() {
        return ip;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getReferrer() {
        return referrer;
    }
}

