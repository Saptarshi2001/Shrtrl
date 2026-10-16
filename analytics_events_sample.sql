-- Run all statements in one ClickHouse session (for example, clickhouse-client --multiquery).
-- sample_events is temporary: it disappears when that session ends.
-- The real events table is not modified.

CREATE TEMPORARY TABLE sample_events
(
    event_id UUID,
    userid UUID,
    shortcode LowCardinality(String),
    occurred_at DateTime64(3, 'UTC'),
    country LowCardinality(String),
    state LowCardinality(String),
    ip String,
    user_agent String,
    referrer String
);

INSERT INTO sample_events
    (event_id, userid, shortcode, occurred_at, country, state, ip, user_agent, referrer)
VALUES
    ('00000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'a1', '2026-10-01 09:15:00.000', 'IN', 'WB',  '198.51.100.10', 'Chrome',  'https://search.example/'),
    ('00000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'a1', '2026-10-01 10:20:00.000', 'IN', 'WB',  '198.51.100.10', 'Chrome',  ''),
    ('00000000-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'b2', '2026-10-01 12:05:00.000', 'US', 'NY',  '203.0.113.20',  'Firefox', 'https://social.example/'),
    ('00000000-0000-0000-0000-000000000004', '22222222-2222-2222-2222-222222222222', 'c3', '2026-10-01 08:00:00.000', 'IN', 'DL',  '192.0.2.30',    'Safari',  ''),
    ('00000000-0000-0000-0000-000000000005', '11111111-1111-1111-1111-111111111111', 'a1', '2026-10-02 14:30:00.000', 'US', 'CA',  '203.0.113.21',  'Firefox', 'https://search.example/'),
    ('00000000-0000-0000-0000-000000000006', '11111111-1111-1111-1111-111111111111', 'b2', '2026-10-02 07:45:00.000', 'GB', 'ENG', '192.0.2.31',    'Safari',  'https://social.example/'),
    ('00000000-0000-0000-0000-000000000007', '11111111-1111-1111-1111-111111111111', 'a1', '2026-10-03 11:10:00.000', 'IN', 'MH',  '198.51.100.11', 'Chrome',  'https://search.example/'),
    ('00000000-0000-0000-0000-000000000008', '11111111-1111-1111-1111-111111111111', 'b2', '2026-10-03 18:20:00.000', 'IN', 'WB',  '198.51.100.12', 'Chrome',  ''),
    ('00000000-0000-0000-0000-000000000009', '22222222-2222-2222-2222-222222222222', 'c3', '2026-10-03 16:35:00.000', 'US', 'CA',  '203.0.113.22',  'Firefox', 'https://search.example/');

-- Display every sample row.
SELECT * FROM sample_events ORDER BY occurred_at, event_id;

-- Practice queries for the first owner. All times are UTC.
SELECT shortcode, count() AS clicks
FROM sample_events
WHERE userid = toUUID('11111111-1111-1111-1111-111111111111')
GROUP BY shortcode
ORDER BY clicks DESC, shortcode;

SELECT toDate(occurred_at) AS day, count() AS clicks
FROM sample_events
WHERE userid = toUUID('11111111-1111-1111-1111-111111111111')
GROUP BY day
ORDER BY day;

SELECT country, state, count() AS clicks
FROM sample_events
WHERE userid = toUUID('11111111-1111-1111-1111-111111111111')
GROUP BY country, state
ORDER BY clicks DESC, country, state;
