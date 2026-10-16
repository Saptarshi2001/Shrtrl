# Shrtrl v1 analytics: queryability report

**Repository snapshot:** 4 October 2026. **Method:** static inspection of the current workspace; no code or tests changed. **Meaning of “queryable”:** an end user can request an analytics result through a working v1 HTTP endpoint. On that definition, **no click analytics are currently queryable through v1**. The controller declares routes, but its methods do not return responses, call a service overload that does not exist, and use SQL that does not match the table. The redirect path does not publish an event. [AnalyticsController.java:20–102](src/main/java/com/shrtrl/analytics/controller/AnalyticsController.java#L20), [AnalyticsService.java:54–58](src/main/java/com/shrtrl/analytics/service/AnalyticsService.java#L54), [UrlController.java:70–88](src/main/java/com/shrtrl/urls/controller/UrlController.java#L70)

This report treats the code as it is, including work in progress in the checkout. It does not treat the older `analytics-module-review.md` as implementation evidence.

## At a glance

| Layer | Current status | What it means for queries |
|---|---|---|
| HTTP analytics API | Eight declared GET methods under **case-sensitive** `/api/v1/Analytics`; none has a usable response implementation | No supported analytics response shape or working endpoint exists. [AnalyticsController.java:20–102](src/main/java/com/shrtrl/analytics/controller/AnalyticsController.java#L20) |
| Query service | `query(String, Map<String,Object>)` delegates to ClickHouse `queryAll` | There is a low-level query primitive, but the controller calls it with one argument and supplies no parameter map. [AnalyticsService.java:54–58](src/main/java/com/shrtrl/analytics/service/AnalyticsService.java#L54) |
| Click data | `events` stores one event row with user, short code, UTC timestamp, geography, IP, user agent, and referrer | These columns can support direct ClickHouse SQL **if rows are present**; they are not a v1 API contract. [001_create_events.sql:1–15](docker/clickhouse/init/001_create_events.sql#L1) |
| Event generation | Redirect resolves and returns 302; event insertion is commented out | Normal redirects generate no analytics rows in this source. [UrlController.java:70–88](src/main/java/com/shrtrl/urls/controller/UrlController.java#L70) |
| Transport | Redis stream or PostgreSQL outbox is selected by configuration; workers insert into ClickHouse | Transport plumbing exists, but its producer has no active redirect caller. [application.properties:16](src/main/resources/application.properties#L16), [RedisWorker.java:45–71](src/main/java/com/shrtrl/analytics/Transport/Redis/RedisWorker.java#L45), [OutboxWorker.java:33–48](src/main/java/com/shrtrl/analytics/Transport/Postgres/OutboxWorker.java#L33) |

## Declared v1 analytics routes

These are **source declarations, not callable analytics features**. All paths use the exact `/api/v1/Analytics` prefix. Every method declares `ResponseEntity<?>` and has no return statement. The service requires `(sql, parameters)`, but the controller passes only SQL. `List` is also used without an import. These are source-level compilation blockers, so no success payload, status, pagination behavior, or live result can be claimed. [AnalyticsController.java:1–15](src/main/java/com/shrtrl/analytics/controller/AnalyticsController.java#L1), [AnalyticsController.java:30–102](src/main/java/com/shrtrl/analytics/controller/AnalyticsController.java#L30), [AnalyticsService.java:54–58](src/main/java/com/shrtrl/analytics/service/AnalyticsService.java#L54)

| GET path | Declared inputs | Intended subject | Current blocker beyond missing return / wrong service call |
|---|---|---|---|
| `/dashboard/live` | session | Recent events | Query has no user filter and uses nonexistent `TIMESTAMP`; it selects raw rows. [Controller:30–36](src/main/java/com/shrtrl/analytics/controller/AnalyticsController.java#L30), [schema:3–11](docker/clickhouse/init/001_create_events.sql#L3) |
| `/dashboard` | `from`, `to` (`LocalDate`); `page=0`, `size=20`; session | Date range | Uses nonexistent `date`, passes no `userid/from/to/size` values, and ignores `page`. [Controller:38–44](src/main/java/com/shrtrl/analytics/controller/AnalyticsController.java#L38) |
| `/dashboard/day` | `date`; `page=0`, `size=20`; session | One day | Nonexistent `date`, malformed predicate, no bound values or pagination. [Controller:47–53](src/main/java/com/shrtrl/analytics/controller/AnalyticsController.java#L47) |
| `/dashboard/month` | `to`; `page=0`, `size=20`; session | Month | Query refers to undeclared `date`; no month boundary is calculated. [Controller:56–62](src/main/java/com/shrtrl/analytics/controller/AnalyticsController.java#L56) |
| `/dashboard/year` | `to`; `page=0`, `size=20`; session | Year | Same query problem; no year boundary is calculated. [Controller:66–72](src/main/java/com/shrtrl/analytics/controller/AnalyticsController.java#L66) |
| `/dashboard/trend` | `from`, `to`; `page=0`, `size=20`; session | Trend | Empty body; no query at all. [Controller:75–80](src/main/java/com/shrtrl/analytics/controller/AnalyticsController.java#L75) |
| `/countries` | `page=0`, `size=20`; session | Country breakdown | Uses nonexistent `countries`, selects `*` with `GROUP BY shortcode`, and has no bound user or date range. [Controller:83–89](src/main/java/com/shrtrl/analytics/controller/AnalyticsController.java#L83), [schema:3–11](docker/clickhouse/init/001_create_events.sql#L3) |
| `/states` | `page=0`, `size=20`; session | State breakdown | Uses nonexistent `states` and the same grouping/parameter problems. [Controller:92–98](src/main/java/com/shrtrl/analytics/controller/AnalyticsController.java#L92) |

Spring Security requires authentication for analytics paths, since only register/login, CSRF, and public URL resolution are permitted anonymously. Authentication is not enough for row authorization: the current live query does not constrain `userid`, and other routes read `USER_ID` but do not bind it to the query. [SecurityConfig.java:23–35](src/main/java/com/shrtrl/config/SecurityConfig.java#L23), [AuthController.java:95–115](src/main/java/com/shrtrl/auth/controller/AuthController.java#L95), [AnalyticsController.java:30–42](src/main/java/com/shrtrl/analytics/controller/AnalyticsController.java#L30)

## Data available for future queries

The ClickHouse `events` table contains `event_id UUID`, `userid UUID`, `shortcode LowCardinality(String)`, `occurred_at DateTime64(3, 'UTC')`, `country`, `state`, `ip`, `user_agent`, and `referrer`. The latter five are strings and default to empty. It is a `MergeTree` partitioned weekly (`toMonday(occurred_at)`) and ordered by `(userid, shortcode, occurred_at, event_id)`. No `clicks`, `date`, `timestamp`, `countries`, `states`, `urlid`, destination URL, group ID, device class, browser, bot flag, or conversion field exists. [001_create_events.sql:1–15](docker/clickhouse/init/001_create_events.sql#L1)

The Java `Event` has matching logical fields and maps to a PostgreSQL `analytics_outbox` table; the outbox is an **ingestion queue**, not the dashboard query source. [Event.java:12–42](src/main/java/com/shrtrl/analytics/entity/Event.java#L12), [V2__create_analytics_outbox.sql:1–15](src/main/resources/db/migration/V2__create_analytics_outbox.sql#L1), [AnalyticsService.java:26–40](src/main/java/com/shrtrl/analytics/service/AnalyticsService.java#L26)

The URL database separately holds `urlid`, `longurl`, `shortcode`, `created_date`, and owning `userid`; group membership is in `group_url`. This supports URL inventory and could supply labels or group filters after deliberate cross-store query design. The ClickHouse events table itself has no group or destination key. [Urls.java:28–53](src/main/java/com/shrtrl/urls/entity/Urls.java#L28), [V3__create_group_url.sql:1–13](src/main/resources/db/migration/V3__create_group_url.sql#L1)

### What the existing columns can support, once collection and API querying work

| Question | Possible measure / grouping | Caveat |
|---|---|---|
| How many clicks? | `count()` by owner and time range; by `shortcode` | Count is event rows, subject to missing producer events and possible replay duplicates. |
| Which links lead? | `GROUP BY shortcode`, rank by count | No title/destination in ClickHouse; join or enrich from URL database. |
| When do clicks happen? | Hour/day/week/month buckets from `occurred_at`; recent minute | Stored timestamp is UTC. Define user timezone and exclusive end boundary in API. |
| Where are clicks from? | `GROUP BY country`, `state`, or both | Fields may be empty; code shows no geolocation enrichment. State names can collide across countries. |
| Who referred traffic? | `GROUP BY referrer`; parse host for domains | Raw referrer can be empty or contain sensitive query strings. Domain rollup is derived, not stored. |
| Which clients clicked? | Group raw `user_agent`; derive browser/device/OS later | No parsed device, browser, or bot dimensions exist. |
| How many apparent unique visitors? | `uniqExact(ip)` or approximate unique IPs | IP is not a reliable person identifier; NAT, proxies, changing IPs, and privacy limits apply. No visitor/session ID exists. |
| Which events occurred? | Raw event list with time and dimensions | Sensitive fields require access control, pagination, and retention rules. |

Each item in this table is a **schema-supported query idea**, not an implemented HTTP endpoint. The raw event fields are enough for those calculations if events arrive; the current redirect path does not send them. [UrlController.java:70–88](src/main/java/com/shrtrl/urls/controller/UrlController.java#L70)

## Illustrative direct ClickHouse SQL

These examples are **operator-level SQL sketches**, not v1 API requests or verified executions. Replace the example UUID and UTC instants. Always scope by the authenticated owner in an application implementation. The half-open range `[from, to)` avoids double counting at adjacent boundaries.

```sql
-- Owner summary for a UTC time range
SELECT count() AS clicks, uniqExact(shortcode) AS clicked_links
FROM events
WHERE userid = toUUID('11111111-1111-1111-1111-111111111111')
  AND occurred_at >= toDateTime64('2026-10-01 00:00:00', 3, 'UTC')
  AND occurred_at <  toDateTime64('2026-10-02 00:00:00', 3, 'UTC');

-- Daily trend, including only days with rows
SELECT toDate(occurred_at, 'UTC') AS day, count() AS clicks
FROM events
WHERE userid = toUUID('11111111-1111-1111-1111-111111111111')
  AND occurred_at >= toDateTime64('2026-10-01 00:00:00', 3, 'UTC')
  AND occurred_at <  toDateTime64('2026-11-01 00:00:00', 3, 'UTC')
GROUP BY day ORDER BY day;

-- Top links for the same owner (add the desired time predicate)
SELECT shortcode, count() AS clicks
FROM events
WHERE userid = toUUID('11111111-1111-1111-1111-111111111111')
GROUP BY shortcode ORDER BY clicks DESC, shortcode ASC LIMIT 20;

-- Country / state breakdown, keeping state paired with country
SELECT country, state, count() AS clicks
FROM events
WHERE userid = toUUID('11111111-1111-1111-1111-111111111111')
GROUP BY country, state ORDER BY clicks DESC LIMIT 20;

-- Last minute, sorted for a stable raw-event view
SELECT event_id, shortcode, occurred_at, country, state, referrer
FROM events
WHERE userid = toUUID('11111111-1111-1111-1111-111111111111')
  AND occurred_at >= now64(3) - INTERVAL 1 MINUTE
ORDER BY occurred_at DESC, event_id DESC LIMIT 20;
```

The schema supports similar groupings for `referrer` and `user_agent`, and `uniqExact(ip)` for unique IP counts. An application should pass typed/bound values through the service's parameter map rather than interpolate user input. [AnalyticsService.java:54–58](src/main/java/com/shrtrl/analytics/service/AnalyticsService.java#L54)

## Key limits and gaps for v1 planning

1. **No click producer:** `EventStorage` is injected into `UrlController`, but its only call in the redirect method is commented out. Cache entries hold only the long URL, so a cache hit also lacks the owner ID needed for an event. [UrlController.java:38–45](src/main/java/com/shrtrl/urls/controller/UrlController.java#L38), [UrlController.java:70–84](src/main/java/com/shrtrl/urls/controller/UrlController.java#L70), [UrlCacheService.java](src/main/java/com/shrtrl/urls/service/UrlCacheService.java)
2. **No analytics HTTP contract:** No successful `ResponseEntity` body, DTO, validation, size cap, stable pagination, or documented timezone behavior is implemented. `page` is never used. [AnalyticsController.java:30–102](src/main/java/com/shrtrl/analytics/controller/AnalyticsController.java#L30)
3. **Authorization risk:** Every future query must bind the session's `USER_ID`; the live SQL currently spans all users. [AuthController.java:109–114](src/main/java/com/shrtrl/auth/controller/AuthController.java#L109), [AnalyticsController.java:30–42](src/main/java/com/shrtrl/analytics/controller/AnalyticsController.java#L30)
4. **Data quality:** The transport workers send batches to ClickHouse, but current `MergeTree` has no event-ID deduplication guarantee. Retried deliveries can inflate counts; empty geo/referrer/client fields also produce unattributed buckets. [RedisWorker.java:63–76](src/main/java/com/shrtrl/analytics/Transport/Redis/RedisWorker.java#L63), [OutboxWorker.java:37–48](src/main/java/com/shrtrl/analytics/Transport/Postgres/OutboxWorker.java#L37), [001_create_events.sql:7–15](docker/clickhouse/init/001_create_events.sql#L7)
5. **Unsupported metrics:** Unique people, sessions, conversions, destination performance after edits, bot-filtered clicks, campaign attribution, and group analytics cannot be stated as native event measures from this schema. They need definitions, extra collection or joins, and in some cases new fields. [001_create_events.sql:1–15](docker/clickhouse/init/001_create_events.sql#L1), [Urls.java:28–53](src/main/java/com/shrtrl/urls/entity/Urls.java#L28)

## Practical v1 query surface to implement

The smallest coherent API would expose an owner-scoped summary, a time series, a per-link ranking, country/state breakdowns, referrer breakdown, and recent events, all with a defined UTC or account-timezone window and bounded results. This is a **recommendation inferred from available columns**, not a statement that these endpoints exist. It requires a working producer, corrected controller/service calls and SQL, response DTOs, owner filtering, and clear handling of late or duplicate events. The current query primitive can execute SQL with a parameter map after those pieces are connected. [AnalyticsService.java:54–58](src/main/java/com/shrtrl/analytics/service/AnalyticsService.java#L54)

## Verification and scope

Findings were checked against the controller, query service, redirect, schema, transport, security, and URL model source in this workspace. No analytics API runtime or ClickHouse query was executed. Because the controller has evident source-level compilation blockers, the report does not infer HTTP behavior from route annotations alone. Existing uncommitted code was preserved; only this report and its HTML companion were added.
