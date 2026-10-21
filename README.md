# Shrtrl

**Shorten links, organize them into groups, and understand how they are used.**

Shrtrl is a Java 21 and Spring Boot backend for a URL shortener. It provides
session-based accounts, owner-scoped link management, optional groups, and click
analytics backed by PostgreSQL, Redis, and ClickHouse. It currently exposes a
backend API; a dashboard frontend is not included.

## Features

| Feature | What it does |
| --- | --- |
| Accounts and login | Register, sign in, view or update your profile, change your password, and delete your account. Passwords are hashed with BCrypt. |
| Session authentication | Login establishes an HTTP session and rotates its ID. State-changing requests require a CSRF token. |
| Short links | Generate a Base62 short code, redirect visitors with HTTP `302`, update the destination, or delete an owned link. |
| Optional groups | A URL can belong to one group or remain ungrouped. A selected group must belong to the URL owner. Deleting a group preserves its links. |
| Ownership checks | Account management uses the logged-in user. Link and group mutations are scoped to that owner. Group details include its ID, name, and links. |
| Redis URL cache | Cache destinations with a configurable expiry. Updates refresh the cache and deletions remove its entry. The current redirect still queries PostgreSQL for the owner needed by analytics. |
| Click capture | Record an event ID, owner ID, short code, timestamp, visitor IP, user agent, and referrer when resolving a link. |
| IP geolocation | Look up public visitor IPs through `ipwho.is` over HTTPS to obtain country and state. Local/private IPs and failed lookups produce unknown locations. This sends the queried IP to an external provider. |
| PostgreSQL outbox | Save clicks durably before a scheduled worker sends batches to ClickHouse. Failed deliveries stay queued for retry. |
| Analytics queries | Retrieve recent activity, current day/month/year counts, and country/state breakdowns from ClickHouse. |
| Schema migrations | Flyway applies PostgreSQL migrations at startup; Hibernate validates the resulting schema. |
| Container tests | Exercise account, group, URL, and transport behavior using disposable test databases. |

## Stack and layout

Java 21 · Spring Boot 4.0.6 · Spring Security · Spring Data JPA · PostgreSQL 16 ·
Redis 7 · ClickHouse 24 · Flyway · Maven Wrapper · JUnit · Testcontainers

```text
src/main/java/com/shrtrl/
  controllers/   HTTP endpoints and response mapping
  DTOs/          Request and response records
  interfaces/    Sealed operation results for auth, users, groups, and URLs
  services/      Application operations, caching, and geolocation
  models/        Database entities
  queries/       Spring Data repositories
  transport/     EventStorage and PostgreSQL/Redis transport implementations
  config/        Security, database clients, and scheduling
src/main/resources/db/migration/
src/test/java/com/shrtrl/
  api/           MockMvc controller tests with mocked services
  services/      Focused service tests
  integration/   Repository, transport, and migration tests
  e2e/           Real HTTP and database flow tests
```

The packages above belong to one Maven application.

## Install and run

Install **JDK 21** and **Docker with Docker Compose**. Maven is supplied by the
wrapper. The commands below use PowerShell from the repository root.

### 1. Get the source and configure the databases

```powershell
git clone https://github.com/Saptarshi2001/Shrtrl.git
cd Shrtrl
Copy-Item .env.example .env
```

Edit `.env` and replace the example database passwords. For this quickstart, set
`CLICKHOUSE_DB=default`: the supplied ClickHouse initialization script creates
`events` in that database. Keep the default database ports and
`POSTGRES_DB=shrtrl` for the commands below.

```powershell
docker compose --env-file .env up -d postgres redis clickhouse
docker compose ps
```

Compose starts the three database services with health checks and persistent
volumes. The initialization scripts run when the ClickHouse data volume is first
created. An existing volume must already contain the `events` table.

### 2. Connect the local Java application

Compose reads `.env`; a Maven-launched application does not automatically load it.
Set the following environment variables, using the same passwords as `.env`:

```powershell
$env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://localhost:5432/shrtrl'
$env:SPRING_DATASOURCE_USERNAME = 'shrtrl'
$env:SPRING_DATASOURCE_PASSWORD = 'your-postgres-password'
$env:REDIS_URI = 'redis://:your-redis-password@localhost:6379'
$env:CLICKHOUSE_ENDPOINT = 'http://localhost:8123'
$env:CLICKHOUSE_USERNAME = 'shrtrl'
$env:CLICKHOUSE_PASSWORD = 'your-clickhouse-password'
$env:CLICKHOUSE_DATABASE = 'default'
$env:ANALYTICS_TRANSPORT = 'postgres'

.\mvnw.cmd spring-boot:run
```

Use a URI-encoded password in `REDIS_URI` if it contains reserved URI characters.
The API listens at `http://localhost:8080`. On Linux/macOS, export the same
variables and use `./mvnw spring-boot:run`.

On startup, Flyway migrates PostgreSQL and the analytics client reads the
ClickHouse `events` schema. All three database services must therefore be
available before starting the application.

To build a JAR without running tests:

```powershell
.\mvnw.cmd -DskipTests package
```

The supplied application Dockerfile runs the full test suite while building.
The dependency-only Compose startup above is the documented development path.

### Configuration

| Setting | Default / purpose |
| --- | --- |
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | Required PostgreSQL connection settings |
| `REDIS_URI` | `redis://localhost:6379`; include credentials when using Compose |
| `URL_CACHE_TTL_SECONDS` | `3600` |
| `CLICKHOUSE_ENDPOINT` | `http://localhost:8123` |
| `CLICKHOUSE_USERNAME`, `CLICKHOUSE_PASSWORD`, `CLICKHOUSE_DATABASE` | Default user, empty password, and `default` database; override to match your server |
| `ANALYTICS_TRANSPORT` | `postgres` |
| `app.analytics.outbox.poll-delay-ms` | `5000`; delay between completed worker runs |
| `app.analytics.outbox.initial-delay-ms` | `5000`; delay before the first worker run |

## Backend API

All routes use the `/api/v1` prefix. Send JSON request bodies with
`Content-Type: application/json`. Keep the session cookies returned by the server.

### Authentication and CSRF

| Method | Path | Request / behavior |
| --- | --- | --- |
| `GET` | `/auth/csrf` | Public. Return the CSRF token and its header name. |
| `POST` | `/auth/register` | Public with CSRF. Body: `username`, `email`, `password`. Return `201` on creation or `409` for an existing username/email. |
| `POST` | `/auth/login` | Public with CSRF. Body: `email`, `password`. Return `200` and establish the session, or `401` for invalid credentials. |
| `POST` | `/auth/logout` | Authenticated with CSRF. Clear authentication and invalidate the session. |

Obtain a token before registration/login and send it in the header named by
`headerName`. Keep the cookies from the token request. Refresh the token after
login before making authenticated mutations.

```powershell
$base = 'http://localhost:8080/api/v1'
$csrf = Invoke-RestMethod "$base/auth/csrf" -SessionVariable session
$headers = @{}
$headers[$csrf.headerName] = $csrf.token

$registration = @{
    username = 'Alice'
    email = 'alice@example.com'
    password = 'a-long-example-password'
} | ConvertTo-Json
Invoke-RestMethod "$base/auth/register" -Method Post -WebSession $session `
    -Headers $headers -ContentType 'application/json' -Body $registration

$login = @{ email = 'alice@example.com'; password = 'a-long-example-password' } |
    ConvertTo-Json
Invoke-RestMethod "$base/auth/login" -Method Post -WebSession $session `
    -Headers $headers -ContentType 'application/json' -Body $login

$csrf = Invoke-RestMethod "$base/auth/csrf" -WebSession $session
$headers[$csrf.headerName] = $csrf.token
Invoke-RestMethod "$base/account" -WebSession $session
```

Usernames currently require 5–20 letters, emails 15–30 characters, and passwords
at least 8 characters. A Google success-handler route exists, but Google OAuth
login is not configured by the current security configuration.

### Account

These endpoints use the logged-in account; no user ID is supplied in the path.

| Method | Path | Request / behavior |
| --- | --- | --- |
| `GET` | `/account` | Return profile fields `userName` and `email`. |
| `PUT` | `/account` | Update using `{"username":"Alicia","email":"alicia@example.com"}`. |
| `PUT` | `/account/password` | Change password using `{"oldPassword":"...","newPassword":"..."}`. |
| `DELETE` | `/account` | Delete the account and invalidate its session. |

### URLs

| Method | Path | Request / behavior |
| --- | --- | --- |
| `POST` | `/urls` | Authenticated. Create using `longUrl` and an optional positive `groupId`; return `201`. |
| `GET` | `/urls/{shortcode}` | Public. Record a visit and return `302` with the destination in the `Location` header. Unknown codes return `404`. |
| `PUT` | `/urls/{shortcode}` | Authenticated owner. Body: `{"longUrl":"https://example.org"}`. Update the destination and cache. |
| `DELETE` | `/urls/{shortcode}` | Authenticated owner. Delete the URL and its cached destination. |

Create an ungrouped URL:

```json
{"longUrl":"https://example.com"}
```

Create a URL in an existing group owned by the logged-in user:

```json
{"longUrl":"https://example.com","groupId":1}
```

The creation response contains `longUrl`, `shortCode`, `createdDate`, and `result`.
The usable short link is `http://localhost:8080/api/v1/urls/{shortCode}`. Creation
currently returns the short code rather than a complete short-link URL.

**Current limit:** URL creation and updates accept at most **20 characters** for
`longUrl`. Some validation messages still mention 2048; the enforced maximum is
20. HTTP and HTTPS are accepted.

### Groups

All group endpoints require authentication and operate on the owner's groups.

| Method | Path | Request / behavior |
| --- | --- | --- |
| `POST` | `/groups` | Body: `{"groupName":"Study Links"}`. Return `201`; duplicate names for the same owner return `409`. |
| `GET` | `/groups/{groupId}` | Return group details and contained URLs; missing/foreign groups return `404`. |
| `PUT` | `/groups/{groupId}` | Body: `{"groupName":"Saved Links"}`. Rename the group. |
| `DELETE` | `/groups/{groupId}` | Remove the group. Its URLs remain usable and become ungrouped. |

Group names must be 5–15 characters. Group and URL IDs are integers; user IDs are
UUIDs. A group details response looks like:

```json
{
  "groupId": 1,
  "groupName": "Study Links",
  "urls": [
    {"longUrl": "https://example.com", "shortCode": "1"}
  ]
}
```

### Analytics

These authenticated routes retain the capitalized `/Analytics` path used by the
controller. They return results from ClickHouse for the logged-in URL owner.

| Method | Path | Current query |
| --- | --- | --- |
| `GET` | `/Analytics/dashboard/live` | Events from the last hour, newest first |
| `GET` | `/Analytics/dashboard?from=2026-10-01&to=2026-10-22` | All stored events for the owner |
| `GET` | `/Analytics/dashboard/day?date=2026-10-22` | Counts for today |
| `GET` | `/Analytics/dashboard/month?to=2026-10-22` | Counts for the current month |
| `GET` | `/Analytics/dashboard/year?to=2026-10-22` | Counts for the current year |
| `GET` | `/Analytics/countries` | Counts grouped by country and short code |
| `GET` | `/Analytics/states` | Counts grouped by country, state, and short code |

The dashboard/date routes currently require the shown date parameters, but the
service queries do **not** use those values. Optional `page` and `size` parameters
default to `0` and `20` and are also not yet applied. Responses expose ClickHouse
records rather than a finalized analytics response DTO. Analytics endpoints are
not covered by the current HTTP E2E suite.

`/live` is a normal HTTP query endpoint. A client can poll it to refresh a view;
there is no WebSocket or server-sent event stream. Newly recorded clicks become
visible after the worker delivers them to ClickHouse.

### Error responses

Controller errors generally use this shape:

```json
{
  "statusCode": 404,
  "errorType": "Not Found",
  "errmsg": "url does not exist"
}
```

Common statuses include `400` for invalid input, `401` for authentication,
`403` for CSRF rejection, `404` for missing or inaccessible resources, `409` for
conflicts, and `500` for operation failures. Security-filter responses can differ
from the controller error body.

## How click delivery works

```text
Visitor requests a short link
  -> resolve URL and identify its owner
  -> look up optional country/state
  -> save Event in PostgreSQL analytics_outbox
  -> return HTTP 302

Scheduled OutboxWorker
  -> lock up to 10,000 queued events, skipping rows held by another worker
  -> send the batch to ClickHouse
  -> delete the delivered batch from PostgreSQL

Analytics API -> query ClickHouse -> return results
```

If delivery fails, the PostgreSQL transaction rolls back and a later poll retries
the batch. Delivery is **at least once**: a crash after ClickHouse accepts the
events but before PostgreSQL commits deletion can produce duplicates. The
existing ClickHouse `MergeTree` table does not enforce event-ID uniqueness.

PostgreSQL is the enabled default transport. Redis stream transport code is also
present, but its worker/consumer-group setup is not the supported quickstart.
Behind a reverse proxy, configure trusted client-IP handling before relying on
the visitor IP: the controller currently uses `request.getRemoteAddr()`.

## How to test

Use a running Docker engine for container-backed tests. E2E tests override the
database URLs and credentials and use random host ports, separate containers,
and no persistent volume mounts. They do not use your development databases.

```powershell
# Compile all production and test sources without running tests
.\mvnw.cmd -DskipTests test-compile

# Run real application flows with disposable PostgreSQL, Redis, and ClickHouse
.\mvnw.cmd -Pe2e test

# Run only the PostgreSQL-to-ClickHouse transport suite
.\mvnw.cmd -Pe2e -Dtest=PostgresTransportE2ETest test
```

On Linux/macOS, use `./mvnw` instead of `.\mvnw.cmd`.

Verified on **22 October 2026**: all **18 E2E tests passed** (5 users, 4 groups,
5 URLs, and 4 PostgreSQL transport tests), with zero failures, errors, or skips.
No suite-labeled test containers remained after the run.

| Test package | Scope |
| --- | --- |
| `api` | Simulated HTTP requests through MockMvc, with mocked service outcomes |
| `services` | Service behavior, failure results, group assignment, and geolocation mapping |
| `integration` | Database mappings, migrations, and transport selection |
| `e2e` | Real HTTP account/group/URL flows and real PostgreSQL-to-ClickHouse delivery |

The `e2e` profile uses `target/e2e` and excludes `AnalyticsController`, whose API
contract is still being developed. Reports are in
`target/e2e/surefire-reports`. Containers are stopped after each test class;
Testcontainers also registers cleanup on JVM exit. Check for leftovers with:

```powershell
docker ps -a --filter "label=com.shrtrl.test-suite=e2e"
```

The default `mvn test` additionally selects older API/integration tests and a
context-load test that requires configured services. Compilation alone does not
establish that this broader suite passes; the isolated E2E command above is the
recommended flow check.

## License

[MIT](LICENSE) — Copyright 2026 Saptarshi Dutta.
