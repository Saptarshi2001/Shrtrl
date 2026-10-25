# Shrtrl

**Shorten links, organize them into groups, and understand how often they are used.**

Shrtrl is a Spring Boot backend for a URL shortener. It provides url shortening,
owner-scoped link management,group creation, and click
analytics.

## Examples

Run these curl commands in Bash (Linux, macOS, or Git Bash) after starting the
app. Replace port `8080` if you changed `APP_PORT`.

### Sign in first

Get a CSRF token and save the session cookies:

```bash
curl -s -c cookies.txt http://localhost:8080/api/v1/auth/csrf
```

Replace `<CSRF_HEADER>` and `<CSRF_TOKEN>` below with the returned `headerName`
and `token`. Register once, then log in:

```bash
curl -i -b cookies.txt -c cookies.txt \
  -H 'Content-Type: application/json' -H '<CSRF_HEADER>: <CSRF_TOKEN>' \
  -d '{"username":"Alice","email":"alice@example.com","password":"ExamplePass123!"}' \
  http://localhost:8080/api/v1/auth/register

curl -i -b cookies.txt -c cookies.txt \
  -H 'Content-Type: application/json' -H '<CSRF_HEADER>: <CSRF_TOKEN>' \
  -d '{"email":"alice@example.com","password":"ExamplePass123!"}' \
  http://localhost:8080/api/v1/auth/login

curl -s -b cookies.txt -c cookies.txt http://localhost:8080/api/v1/auth/csrf
```

Use the **new** token and header name from the last command for the POST
requests below.

### Create a group

```bash
curl -i -b cookies.txt \
  -H 'Content-Type: application/json' -H '<CSRF_HEADER>: <CSRF_TOKEN>' \
  -d '{"groupName":"Study Links"}' \
  http://localhost:8080/api/v1/groups
```

### Shorten a URL

```bash
curl -i -b cookies.txt \
  -H 'Content-Type: application/json' -H '<CSRF_HEADER>: <CSRF_TOKEN>' \
  -d '{"longUrl":"https://example.com"}' \
  http://localhost:8080/api/v1/urls
```

Copy `shortCode` from the response. Visit the short link to record a click:

```bash
curl -i http://localhost:8080/api/v1/urls/<SHORT_CODE>
```

Replace `<SHORT_CODE>` with the returned value. The response is a `302` redirect
with the original URL in the `Location` header.

### Display analytics

Allow a few seconds for the background worker to deliver the click, then run:

```bash
# Recent visits
curl -s -b cookies.txt http://localhost:8080/api/v1/Analytics/dashboard/live

# Click counts by country
curl -s -b cookies.txt http://localhost:8080/api/v1/Analytics/countries

# Click counts by country and state
curl -s -b cookies.txt http://localhost:8080/api/v1/Analytics/states
```

## Requirements

- Docker with Docker Compose
Java, Maven, PostgreSQL, Redis, and ClickHouse are supplied by the Docker build and containers.

## layout


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

## Setup

Install **Docker with Docker Compose** and start Docker. Run the following
commands in PowerShell from the repository root. No local Java or Maven
installation is needed to run the application through Docker.

### 1. Get the source and configure the services

```powershell
git clone https://github.com/Saptarshi2001/Shrtrl.git
cd Shrtrl
Copy-Item .env.example .env
```

Edit `.env` and replace the example PostgreSQL, Redis, and ClickHouse passwords.
Keep the other example values for the initial setup. Compose reads `.env` and
passes the connection settings to the application container automatically.

### 2. Build and start the application

```powershell
docker compose --env-file .env up --build -d
docker compose ps
docker compose logs -f app
```

Compose builds the Java application image and starts the app, PostgreSQL, Redis,
and ClickHouse. The app starts after the database health checks pass. Wait for
the Spring Boot startup message in the app logs, then use the API at
`http://localhost:8080` (or the `APP_PORT` configured in `.env`). Press Ctrl+C
to stop following logs; the containers continue running.

The image build downloads Maven dependencies and packages the application with
tests skipped. Run the test suite separately as described below; local Maven
test commands require JDK 21.

Database data is stored in persistent Docker volumes. ClickHouse initialization
scripts create the `events` table on the first initialization of its data volume.
Reused volumes must already contain that table. Flyway applies PostgreSQL
migrations when the application starts.

To stop the services while keeping their data:

```powershell
docker compose down
```

On Linux/macOS, use `cp .env.example .env` for the copy step; the Docker Compose
commands are the same.

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



## How to test

Start Docker with Linux containers enabled, then run these commands from the
repository root. No local Java or Maven installation is needed, and the
application containers do not need to be running.

```powershell
# Build the test image with Java 21, Maven, and the test sources
docker compose -f compose.test.yaml build tests

# Run the complete test suite
docker compose -f compose.test.yaml run --rm tests test
```



To run only the E2E tests through Docker:

```powershell
docker compose -f compose.test.yaml run --rm tests -Pe2e test
```

| Test package | Scope |
| --- | --- |
| `api` | Simulated HTTP requests through MockMvc, with mocked service outcomes |
| `services` | Service behavior, failure results, group assignment, and geolocation mapping |
| `integration` | Database mappings, migrations, and transport selection |
| `e2e` | Real HTTP account/group/URL flows and real PostgreSQL-to-ClickHouse delivery |


