# Analytics Module Review

This document reviews the analytics implementation currently present in `src/main/java/com/shrtrl/analytics`, plus the ClickHouse and Redis configuration that it depends on. It intentionally covers the analytics part first; the URL module should be reviewed separately after the analytics design is settled.

## Executive summary

The analytics module is currently a draft and cannot compile. It also is not connected to the redirect path, so no click event reaches Redis, PostgreSQL, or ClickHouse.

The design should expose one analytics publishing interface and select one transport at application startup:

```text
GET /api/v1/urls/{shortcode}
        |
        v
EventStorage.publish(clickEvent)
        |
        +-- app.analytics.transport=redis
        |       Redis Stream -> RedisStreamWorker --+
        |                                           |
        +-- app.analytics.transport=postgres         v
                Outbox table -> OutboxWorker ----> AnalyticsService -> ClickHouse
```

Support **both Redis Streams and a PostgreSQL outbox as available implementations**, but activate exactly one implementation for a running deployment. The person deploying the application selects it with configuration:

```properties
app.analytics.transport=${ANALYTICS_TRANSPORT:redis}
```

Valid values should be `redis` and `postgres`. The selected publisher and its matching worker become active; the other pair remains inactive. Redis is a sensible default for click telemetry. PostgreSQL remains selectable when stronger durability matters more than the extra database write per redirect.

This review interprets “the user chooses” as a deployment/startup choice. Choosing a different transport for every application account would require stored per-user configuration and both workers running simultaneously, which is a different and significantly more complex requirement.

## Decisions required before implementation

These requirements materially affect the design:

1. **Delivery guarantee:** Are occasional lost or duplicated click events acceptable, or must every event be retained?
2. **Analytics delay:** Is approximately one second of delay acceptable?
3. **Retention:** How long must raw IP addresses, user agents, referrers, and click events be retained?
4. **Timezone:** Should dashboard date boundaries use UTC or each user's timezone?
5. **Deployment:** Will more than one application instance consume the Redis stream?

This review assumes at-least-once delivery, approximately one-second batching latency, UTC storage, and the possibility of multiple application instances.

## Transport selection

Keep `EventStorage` as the application-facing interface because two implementations are now an explicit requirement:

```java
public interface EventStorage {
    void insertEvent(ClickEvent event);
}
```

Activate the Redis publisher only for the Redis selection:

```java
@Component
@ConditionalOnProperty(
    name = "app.analytics.transport",
    havingValue = "redis"
)
public class RedisEventInsertion implements EventStorage {
    // XADD the event to analytics:clicks
}
```

Activate the PostgreSQL publisher only for the PostgreSQL selection:

```java
@Component
@ConditionalOnProperty(
    name = "app.analytics.transport",
    havingValue = "postgres"
)
public class PostgresEventInsertion implements EventStorage {
    // Insert the event into the PostgreSQL outbox
}
```

Apply the same condition to the matching workers:

```text
redis   -> RedisEventInsertion + RedisStreamWorker
postgres -> PostgresEventInsertion + OutboxWorker
```

`UrlController` or, preferably, the URL service injects only `EventStorage`:

```java
private final EventStorage eventStorage;
```

Spring injects the one implementation enabled by `app.analytics.transport`. Do not read configuration manually inside the controller, do not use a mutable `TransportLayer` field, and do not switch transports on every redirect.

Fail application startup for a missing or unsupported value. A typed configuration property or explicit validation should restrict the value to `redis` or `postgres`.

## Critical problems

### 1. The analytics source files do not compile

Most classes below `analytics/Transport` and `analytics/entity/Event.java` have no `package` declaration. Several also lack required imports. Java code in the default package cannot form a usable module with the packaged `com.shrtrl.analytics` classes.

Examples include:

- `Event.java`
- `EventStorage.java`
- `TransportLayer.java`
- `RedisEventInsertion.java`
- `RedisWorker.java`
- `PostgresEventInsertion.java`
- `OutboxRepository.java`
- `OutboxWorker.java`

Additional direct compilation failures include:

- `TransportLayer` is not valid enum syntax.
- `JpaRepository<>` has no entity and ID generic types.
- `nalyticsService` is misspelled.
- `RedisCommand` should be `RedisCommands` for the current Lettuce API.
- `while(1)` is invalid Java because a loop condition must be boolean.
- `msg` is declared inside the loop and referenced outside its scope.
- `messages` and `commands` are referenced but never declared.
- `XReadArgs.StreamOffset` is not the correct type location; `StreamOffset` is its own Lettuce type.
- `@Scheduled(5000)` does not express a valid fixed delay.
- Scheduled methods return `int` even though their return value has no consumer.
- `AnalyticsService.insert(...)` is called, but that method does not exist.
- `EventStorage` refers to `Event` without a usable package/import relationship.
- `RedisEventInsertion` calls `ObjectMapper.writeValueAsString` as if it were static and does not handle its checked exception.

The controller has its own compilation failures:

- Every endpoint declares `ResponseEntity<?>` but returns nothing.
- Every call to `analyticsclient.query(...)` omits the required parameter map.
- `GenericRecord` is imported twice.

### 2. No redirect produces an analytics event

`UrlController.resolveUrl` looks up the destination and returns the redirect, but it never calls `EventStorage`, `RedisEventInsertion`, or any other analytics component. Therefore the ingestion pipeline has no producer.

There is a second integration problem: the URL cache currently stores only `shortcode -> longUrl`. On a cache hit, the redirect handler does not know the owning `userid`, yet the ClickHouse schema requires `userid`. The cached value must include the identifiers needed to construct an event, for example:

```text
shortcode -> { longUrl, userId, urlId }
```

Alternatively, redesign the event/schema so ownership can be resolved without another PostgreSQL lookup. Do not perform a database lookup on every cache hit merely for analytics.

### 3. The event model does not match the ClickHouse table

The Java `Event` fields are:

```text
userid, country, state, ip, datetime, shortcode, clicks, timestamp
```

The ClickHouse columns are:

```text
event_id, userid, shortcode, occurred_at,
country, state, ip, user_agent, referrer
```

Problems:

- Java has both `datetime` and `timestamp`; ClickHouse has only `occurred_at`.
- Java has `clicks`; ClickHouse does not. One row should represent one click, so a `clicks` field is unnecessary.
- Java lacks `event_id`, `user_agent`, and `referrer`.
- Java uses `InetAddress`, while ClickHouse currently stores `ip` as `String`.
- All Java fields are private and there are no getters, constructor, or record components. A default Jackson configuration may serialize this object incorrectly or as an empty object.
- Naming is inconsistent (`userid` instead of `userId`).

Replace it with one immutable event DTO/record whose serialized field names deliberately match ClickHouse. Generate a stable `event_id` before writing to Redis so a retry preserves the same ID.

Suggested logical fields:

```text
eventId: UUID
userId: UUID
shortCode: String
occurredAt: Instant
country: String
state: String
ip: String
userAgent: String
referrer: String
```

### 4. Successful delivery is not confirmed

`AnalyticsService.insertData` calls `Client.insert(...)` and ignores its `CompletableFuture`. The method can return before ClickHouse confirms the insert, and asynchronous failures are lost.

This is dangerous for Redis consumption:

```text
start ClickHouse insert
return immediately
XACK Redis messages
ClickHouse insert later fails
events are lost
```

The service must wait for or otherwise propagate the completion result. Redis messages may be acknowledged only after ClickHouse confirms success.

### 5. The ClickHouse input format is probably wrong for a batch

`AnalyticsService` uses `ClickHouseFormat.JSONAsObject`. The proposed stream contains one JSON object per event. A batch should be encoded with a row-oriented format supported by the table, such as `JSONEachRow`, or use the client's registered-object collection insertion API.

The service should expose a domain-level operation such as:

```text
insertEvents(List<Event> events)
```

The worker should call `AnalyticsService`; it should not know ClickHouse table names, formats, or serialization details.

## Redis path review

### Producer: `RedisEventInsertion`

Current problems:

- It is not a Spring bean, so nothing creates or injects it.
- `RedisCommands` is never injected and will be `null` if the class is constructed manually.
- `ObjectMapper` is neither imported nor injected.
- Serialization failure is not handled.
- `@Transactional` does not make a standalone Redis `XADD` part of a PostgreSQL transaction and serves no useful purpose here.
- The returned stream ID is assigned and then ignored.
- There is no stream length/retention policy.
- Nothing calls this producer from the redirect path.

Required changes:

1. Give the class a proper package and make it a Spring service/component.
2. Constructor-inject `RedisCommands<String, String>` and `ObjectMapper`.
3. Serialize the corrected event record.
4. Execute `XADD analytics:clicks * payload <json>`.
5. Remove `@Transactional`.
6. Decide whether Redis failure should be logged and ignored so redirects remain available, or whether the request should fail. Analytics normally should not make redirects unavailable.
7. Add stream trimming/retention, because `XACK` removes an entry from the pending list but does not remove it from the stream.

### Consumer: `RedisWorker`

The current class is not salvageable through small syntax fixes; its lifecycle and reliability behavior need to be rewritten.

Required behavior:

1. Start one long-running consumer when the Spring application is ready.
2. Use `XREADGROUP` with a bounded `COUNT` and bounded `BLOCK` time.
3. Use a **dedicated Lettuce connection** for the blocking read. The existing shared synchronous connection is also used by the URL cache; a blocking command must not monopolize it.
4. Use a unique consumer name for every running application instance. Hard-coded `worker-1` causes collisions when multiple instances run.
5. Pass the extracted event batch to `AnalyticsService.insertEvents(...)`.
6. `XACK` the Redis message IDs only after the ClickHouse insertion completes successfully.
7. Leave failed messages pending and retry with backoff.
8. Recover messages abandoned by crashed consumers using `XAUTOCLAIM`/pending-entry processing.
9. Log failures and expose counters for received, inserted, retried, pending, and dead-lettered messages.
10. Stop the blocking connection and worker cleanly during application shutdown.

Do not combine `@Scheduled` with an infinite blocking loop. Choose one lifecycle:

- Redis Streams: one managed, long-running blocking consumer.
- PostgreSQL outbox: a short `@Scheduled(fixedDelay = ...)` polling method.

### What the Lettuce read arguments mean

This Java call:

```java
commands.xreadgroup(
    Consumer.from("clickhouse-workers", consumerName),
    XReadArgs.Builder.count(10_000).block(Duration.ofSeconds(1)),
    StreamOffset.lastConsumed("analytics:clicks")
)
```

represents this Redis command:

```text
XREADGROUP
GROUP clickhouse-workers <consumerName>
COUNT 10000
BLOCK 1000
STREAMS analytics:clicks >
```

The parts mean:

- `Consumer.from(...)`: identifies the consumer group and this worker instance. It does not create a thread.
- `XReadArgs`: optional read settings. `COUNT` is the maximum number returned; `BLOCK` is how long Redis may wait when no new event is available.
- `StreamOffset.lastConsumed(...)`: identifies the stream and asks the group for new, not-yet-delivered entries (`>`).

`COUNT 10000` is not a minimum and does not wait for 10,000 messages. If only one event is available, Redis can return one. To protect ClickHouse from tiny inserts, either accumulate locally until `10,000 events OR 1 second`, or enable reliable ClickHouse asynchronous inserts and let ClickHouse batch compatible requests. Start with a maximum batch of 10,000 and a maximum latency of one second, then measure.

### Redis consumer-group setup

The consumer group must exist before `XREADGROUP`:

```text
XGROUP CREATE analytics:clicks clickhouse-workers 0 MKSTREAM
```

Application startup may create it idempotently and ignore only the specific `BUSYGROUP` error that means it already exists. Other Redis failures must be surfaced.

### Redis durability

The current Compose service uses a volume but does not explicitly enable AOF. Default Redis snapshotting can lose recent stream entries after a crash. If the application requires stronger durability, enable and test AOF or select the PostgreSQL outbox design.

## PostgreSQL outbox path review

The outbox implementation is incomplete and must not be enabled alongside the Redis worker.

Problems:

- No outbox table exists in the Flyway migrations.
- `Event` is not a JPA entity and has no primary key mapping.
- `OutboxRepository extends JpaRepository<>` lacks its generic types.
- `findAll()` is unbounded; it can exhaust memory and repeatedly resend the entire table.
- Rows are never claimed, marked complete, retried, or deleted.
- There is no `LIMIT`, ordering, `SKIP LOCKED`, retry count, lease, or next-attempt timestamp.
- `PostgresEventInsertion` is not a Spring bean.
- `OutboxWorker` has compile errors and calls a nonexistent analytics method.
- Holding `@Transactional` open across a ClickHouse network operation is unsafe and does not create a distributed transaction between PostgreSQL and ClickHouse.
- A crash after ClickHouse succeeds but before PostgreSQL is marked complete produces duplicates; idempotency is still required.

If this path is chosen, add a proper outbox entity/table with at least:

```text
event_id
payload
status
created_at
claimed_at
claim_owner
attempt_count
next_attempt_at
last_error
```

Claim a bounded batch in a short PostgreSQL transaction, commit the claim, call ClickHouse outside that transaction, then mark success or retry in another short transaction.

## `AnalyticsService` review

Keep `AnalyticsService` as the only application-level entry point to ClickHouse. Workers and controllers should use it instead of using `Client` directly.

Required changes:

- Rename the field from `clickevent` to `clickHouseClient`.
- Replace arbitrary `insertData(String table, InputStream data)` with a typed `insertEvents(List<Event> events)` operation.
- Fix the batch format and propagate asynchronous insertion failures.
- Replace the generic `query(String, Map)` API exposed to controllers with specific methods such as `getLiveSummary`, `getTimeSeries`, `getCountryBreakdown`, and `getStateBreakdown`.
- Keep SQL and ClickHouse parameter construction inside the service/repository layer.
- Return application DTOs, not ClickHouse `GenericRecord` objects.
- Add timeouts and structured logging without logging raw IP addresses or complete referrers.

An empty `AnalyticsRepository.java` currently serves no purpose. Either implement query persistence there and keep orchestration in the service, or remove it. For this small project, putting ClickHouse queries directly in `AnalyticsService` is acceptable and simpler.

## Controller and query review

`AnalyticsController` is unfinished and unsafe.

### Compilation and HTTP problems

- All methods fail to return their `ResponseEntity`.
- The controller calls the service with the wrong number of arguments.
- `showTrends` is empty.
- `page` is accepted but never used.
- `size` is not validated or capped.
- Endpoint casing is inconsistent: `/api/v1/Analytics` should be lowercase.
- `@RestController` better expresses this JSON API than `@Controller`.
- Raw `GenericRecord` responses leak the database-client representation into the HTTP contract.

### Authorization problem

`/dashboard/live` has no `userid` predicate, which would expose every user's events to any authenticated user. Every analytics query must derive the user ID from the authenticated session and filter by it. Do not accept a user ID supplied by the request.

### Invalid ClickHouse SQL

The table is created as `events`, but queries use `EVENTS`. Queries also reference columns that do not exist:

- `TIMESTAMP` does not exist; use `occurred_at`.
- `date` does not exist; derive/filter using `occurred_at`.
- `countries` does not exist; the column is `country`.
- `states` does not exist; the column is `state`.

Other query errors include:

- `:id={userid}` is not valid ClickHouse parameter usage.
- Placeholders are sometimes placed inside quotes, turning them into literal text.
- `IN` is used where a range/equality expression is needed.
- `GROUP BY shortcode ORDER BY countries` does not produce a country breakdown.
- Queries select raw rows when the endpoint names imply aggregates.
- Date ranges do not define inclusive/exclusive boundaries or a timezone.
- Pagination lacks `OFFSET` or keyset state and has no stable `ORDER BY`.

Use typed ClickHouse parameters, always filter by the session's user ID, and implement endpoint-specific aggregate queries.

### Suggested API surface

Avoid separate day/month/year endpoints containing near-identical logic. A smaller API is easier to maintain:

```text
GET /api/v1/analytics/summary?from=<instant>&to=<instant>
GET /api/v1/analytics/timeseries?from=<instant>&to=<instant>&bucket=hour|day|month
GET /api/v1/analytics/countries?from=<instant>&to=<instant>
GET /api/v1/analytics/states?from=<instant>&to=<instant>&country=IN
GET /api/v1/analytics/live
```

Return purpose-built DTOs such as totals, time buckets, and `{name, clicks}` breakdown items.

## ClickHouse schema review

The initial table is a reasonable starting point, but it needs alignment with actual queries and retry semantics.

Required decisions/changes:

- Supply `event_id` from the event producer instead of generating a new UUID on every insertion attempt. Otherwise a retry always looks like a new event.
- Decide how duplicates will be handled. Redis consumer groups and outbox delivery are at-least-once, while the current plain `MergeTree` does not make retried rows idempotent.
- Consider `ORDER BY (userid, occurred_at, shortcode, event_id)` if the dominant query is a user-wide time range. The current `(userid, shortcode, occurred_at, event_id)` is strongest for one user's one-shortcode range.
- Add a TTL if raw events should expire.
- Decide whether `ip` should use ClickHouse's IP address types and whether it should be anonymized.
- Confirm whether country/state enrichment happens before enqueueing or in the worker.
- Keep all timestamps in UTC and convert only at the API boundary.

## Privacy and security

The schema stores IP addresses, user agents, and referrers. These can be personal or sensitive data.

Required controls:

- Define retention and deletion requirements.
- Consider truncating/anonymizing IP addresses.
- Do not trust arbitrary `X-Forwarded-For` headers; configure trusted proxies before deriving the client IP.
- Avoid logging raw event payloads.
- Referrer values may contain query-string secrets or personal data; sanitize them before storage.
- Ensure analytics endpoints are user-scoped and covered by authorization tests.

## Dependency and configuration cleanup

- Both Jedis and Lettuce are declared, but the code uses Lettuce. Remove the unused Jedis dependency.
- Enable scheduling in application configuration for the PostgreSQL worker. The `OutboxWorker` itself must be conditional, so no outbox task is registered when Redis is selected.
- Pass `ANALYTICS_TRANSPORT` through `compose.yaml` to `app.analytics.transport`; default it deliberately rather than selecting a transport inside Java code.
- Add configurable stream name, group name, batch size, block duration, retry/backoff, and consumer identity instead of hard-coding them.
- Give the Redis stream worker its own connection bean/lifecycle instead of using the cache connection.
- Add ClickHouse insert/query timeouts and worker shutdown settings.
- Package directories should be lowercase: use `transport`, `transport.redis`, and `transport.postgres`, not `Transport`.

## Recommended package structure

Keep both selectable transports, while sharing the event contract and analytics service:

```text
com.shrtrl.analytics
|-- controller
|   `-- AnalyticsController
|-- dto
|   |-- AnalyticsSummaryResponse
|   |-- BreakdownItemResponse
|   `-- TimeSeriesPointResponse
|-- event
|   `-- ClickEvent
|-- service
|   `-- AnalyticsService
`-- transport
    |-- EventStorage
    |-- redis
    |   |-- RedisEventInsertion
    |   `-- RedisStreamWorker
    `-- postgres
        |-- PostgresEventInsertion
        |-- OutboxEvent
        |-- OutboxRepository
        `-- OutboxWorker
```

`EventStorage` is justified here because interchangeable Redis and PostgreSQL implementations are an explicit requirement. Both implementations must be complete and tested, but Spring must create only the publisher and worker selected by `app.analytics.transport`.

## Implementation order

1. Add and validate `app.analytics.transport=redis|postgres`, then conditionally activate exactly one publisher and its matching worker.
2. Create the corrected immutable click-event model matching ClickHouse.
3. Fix `AnalyticsService` so batch insertion is typed and completion/failure is observable.
4. Implement both producers and connect the selected `EventStorage` implementation to successful URL redirects. Define whether analytics publishing failure may break a redirect.
5. Implement the Redis consumer-group lifecycle, acknowledgment, pending recovery, retention, and dedicated connection.
6. Implement the bounded PostgreSQL claim/retry flow and its Flyway migration.
7. Fix ClickHouse retry/idempotency behavior and align the sort key with queries.
8. Replace generic controller queries with user-scoped service methods and response DTOs.
9. Add tests and operational metrics.

## Minimum test coverage

- Event JSON serialization matches the ClickHouse column contract.
- A successful redirect publishes exactly one event.
- `app.analytics.transport=redis` creates only the Redis publisher/worker.
- `app.analytics.transport=postgres` creates only the PostgreSQL publisher/worker.
- Missing or invalid transport configuration fails startup with a clear error.
- An analytics publishing failure follows the chosen redirect-availability policy.
- Redis worker inserts a received batch and acknowledges it only after ClickHouse success.
- Redis worker does not acknowledge a failed ClickHouse batch.
- Pending messages are reclaimed after a consumer crash.
- Duplicate delivery follows the chosen idempotency policy.
- Every dashboard endpoint filters by the authenticated user.
- Date boundaries, UTC conversion, empty results, and maximum page size are correct.
- Application shutdown stops the worker without acknowledging unfinished work.

## Current verification status

Static inspection confirms the compilation and design problems listed above. A Maven compilation could not be completed in the current environment because the PowerShell Maven wrapper failed before launching Maven (`Cannot index into a null array`) and no system `mvn` executable is installed. After the source-level compilation failures are corrected, repair or regenerate the Maven wrapper and run:

```text
./mvnw test
```

The analytics implementation should not be considered functional until that build and the ingestion integration tests pass.
