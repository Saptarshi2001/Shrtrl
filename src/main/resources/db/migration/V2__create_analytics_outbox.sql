CREATE TABLE analytics_outbox
(
    event_id UUID PRIMARY KEY,
    userid UUID NOT NULL,
    shortcode VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    country VARCHAR(100),
    state VARCHAR(100),
    ip VARCHAR(45),
    user_agent TEXT,
    referrer TEXT
);

CREATE INDEX idx_analytics_outbox_occurred_at
    ON analytics_outbox(occurred_at);
