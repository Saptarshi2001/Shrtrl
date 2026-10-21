USE shrtrl_e2e;

CREATE TABLE IF NOT EXISTS events
(
    event_id UUID DEFAULT generateUUIDv4(),
    userid UUID,
    shortcode LowCardinality(String),
    occurred_at DateTime64(3, 'UTC') DEFAULT now64(3),
    country LowCardinality(String) DEFAULT '',
    state LowCardinality(String) DEFAULT '',
    ip String DEFAULT '',
    user_agent String DEFAULT '',
    referrer String DEFAULT ''
)
ENGINE = MergeTree
PARTITION BY toMonday(occurred_at)
ORDER BY (userid, shortcode, occurred_at, event_id);
