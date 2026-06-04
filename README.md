# Shrtrl

## Compose file review

This project has a `compose.yaml`, but it has several issues that will stop it from working reliably.

### 1. `POSTGRES_*`, `REDIS_*`, and other environment variables use invalid syntax

Current example:

```yaml
environment:
  - POSTGRES_USER: $ {postgres_user}
```

Problems:

- List-style environment variables must use `KEY=value`, not `KEY: value`.
- `$ {postgres_user}` has a space between `$` and `{`, so Compose will not read it as a variable.

Use either mapping style:

```yaml
environment:
  POSTGRES_USER: ${POSTGRES_USER}
  POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
  POSTGRES_DB: ${POSTGRES_DB}
```

Or list style:

```yaml
environment:
  - POSTGRES_USER=${POSTGRES_USER}
  - POSTGRES_PASSWORD=${POSTGRES_PASSWORD}
  - POSTGRES_DB=${POSTGRES_DB}
```

Mapping style is cleaner.

### 2. `depends_on` is backwards

Current structure:

```yaml
postgres:
  depends_on: app
```

This says Postgres depends on the app, so Compose starts the app before Postgres. For this project, the app should depend on its services instead.

Better:

```yaml
app:
  depends_on:
    - postgres
    - redis
    - clickhouse
```

### 3. Top-level `volumes` and `networks` are missing

The services reference these:

```yaml
postgres_data:
clickhouse_data:
redis_data:
shrtrl-container:
```

But they are not declared at the bottom of the Compose file. Add:

```yaml
volumes:
  postgres_data:
  clickhouse_data:
  redis_data:

networks:
  shrtrl-container:
```

### 4. The app config volume path is probably wrong

Current:

```yaml
volumes:
  - "/.config.env:/config/config.env"
```

`/.config.env` means an absolute file at the root of the host filesystem. On Windows, this is probably not where your project config file is.

If the file is in this project folder, use:

```yaml
volumes:
  - ./.config.env:/config/config.env
```

But for normal Spring Boot configuration, prefer environment variables instead of mounting a custom env file.

### 5. Port variable names should be consistent and uppercase

Current:

```yaml
ports:
  - "${port}:8080"
  - "${postgresport}:5432"
```

This can work if those variables exist, but uppercase names are the usual convention:

```yaml
ports:
  - "${APP_PORT}:8080"
  - "${POSTGRES_PORT}:5432"
```

Then define them in `.env`.

### 6. `postgres:latest`, `redis:latest`, and `clickhouse:latest` are not ideal

`latest` can change unexpectedly and break your setup later.

Prefer pinned versions, for example:

```yaml
postgres:16
redis:7
clickhouse/clickhouse-server:24
```

### 7. Redis environment variables do not configure Redis auth by themselves

This does not actually create a Redis user/password in the official Redis image:

```yaml
environment:
  REDIS_USER: ${REDIS_USER}
  REDIS_PASSWORD: ${REDIS_PASSWORD}
  REDIS_DB: ${REDIS_DB}
```

For simple local password auth, use a command:

```yaml
command: ["redis-server", "--requirepass", "${REDIS_PASSWORD}"]
```

Redis databases are selected by the client using a database number. `REDIS_DB` is not automatically applied by the Redis container.

### 8. ClickHouse port `9440` is the secure native port

Current:

```yaml
ports:
  - "${clickport}:9440"
```

ClickHouse commonly exposes:

- `8123` for HTTP
- `9000` for native TCP
- `9440` for secure native TCP

Only use `9440` if your app is configured for secure native ClickHouse connections. Otherwise, `8123` or `9000` may be more appropriate.

### 9. The app has no database configuration yet

`src/main/resources/application.properties` currently only has:

```properties
spring.application.name=Shrtrl
```

That means the Spring Boot app is not yet configured to connect to Postgres, Redis, or ClickHouse. Compose can start the containers, but the app still needs properties like:

```properties
spring.datasource.url=jdbc:postgresql://postgres:5432/${POSTGRES_DB}
spring.datasource.username=${POSTGRES_USER}
spring.datasource.password=${POSTGRES_PASSWORD}
```

Inside Compose, the hostname is the service name, so the app should connect to `postgres`, not `localhost`.

## Example corrected shape

```yaml
services:
  app:
    build:
      context: .
    ports:
      - "${APP_PORT}:8080"
    depends_on:
      - postgres
      - redis
      - clickhouse
    networks:
      - shrtrl-container

  postgres:
    image: postgres:16
    ports:
      - "${POSTGRES_PORT}:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    environment:
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
      POSTGRES_DB: ${POSTGRES_DB}
    networks:
      - shrtrl-container

  redis:
    image: redis:7
    ports:
      - "${REDIS_PORT}:6379"
    command: ["redis-server", "--requirepass", "${REDIS_PASSWORD}"]
    volumes:
      - redis_data:/data
    networks:
      - shrtrl-container

  clickhouse:
    image: clickhouse/clickhouse-server:24
    ports:
      - "${CLICKHOUSE_HTTP_PORT}:8123"
    volumes:
      - clickhouse_data:/var/lib/clickhouse
    environment:
      CLICKHOUSE_USER: ${CLICKHOUSE_USER}
      CLICKHOUSE_PASSWORD: ${CLICKHOUSE_PASSWORD}
      CLICKHOUSE_DB: ${CLICKHOUSE_DB}
    networks:
      - shrtrl-container

volumes:
  postgres_data:
  redis_data:
  clickhouse_data:

networks:
  shrtrl-container:
```

## Example `.env`

Do not commit real production secrets. For local development, a `.env` file could look like:

```env
APP_PORT=8080

POSTGRES_PORT=5432
POSTGRES_USER=shrtrl
POSTGRES_PASSWORD=shrtrl_dev_password
POSTGRES_DB=shrtrl

REDIS_PORT=6379
REDIS_PASSWORD=shrtrl_redis_password

CLICKHOUSE_HTTP_PORT=8123
CLICKHOUSE_USER=shrtrl
CLICKHOUSE_PASSWORD=shrtrl_clickhouse_password
CLICKHOUSE_DB=shrtrl
```

## Dockerfile review

Current `DOCKERFILE`:

```dockerfile
FROM: Java:21--alpine
WORKDIR /Shrtrl
COPY mvn install
COPY . .
COPY mvn test
EXPOSE ${APP_PORT}
CMD ["mvn","run"]
```

This file has several issues.

### 1. `FROM` syntax is invalid

Current:

```dockerfile
FROM: Java:21--alpine
```

Dockerfile instructions do not use `:` after the instruction name.

Also, `Java:21--alpine` is not a normal Java image name. Docker image names are case-sensitive in practice, and official Java runtime images usually come from Eclipse Temurin.

Use something like:

```dockerfile
FROM eclipse-temurin:21-jdk-alpine
```

### 2. `COPY mvn install` does not run Maven

Current:

```dockerfile
COPY mvn install
```

`COPY` copies files. It does not execute commands.

If you want to run Maven, use `RUN`:

```dockerfile
RUN ./mvnw clean package
```

This project has `mvnw` and `mvnw.cmd`, not a plain `mvn` file. Inside Linux containers, use `./mvnw`.

### 3. `COPY mvn test` also does not run tests

Current:

```dockerfile
COPY mvn test
```

Same issue: this tries to copy a file/folder named `mvn` into a destination named `test`. It does not run tests.

Use:

```dockerfile
RUN ./mvnw test
```

or let `clean package` run tests by default.

### 4. `EXPOSE ${APP_PORT}` is not the right port

Your Spring Boot app listens inside the container on `8080` by default.

Use:

```dockerfile
EXPOSE 8080
```

Then Compose maps the host port:

```yaml
ports:
  - "${APP_PORT}:8080"
```

`APP_PORT` is the host-side port. The container-side port should usually stay `8080`.

### 5. `CMD ["mvn","run"]` is not a valid Spring Boot start command

Current:

```dockerfile
CMD ["mvn","run"]
```

Maven does not have a `run` lifecycle command like that.

For Spring Boot development, the Maven command is:

```shell
./mvnw spring-boot:run
```

But for Docker images, it is usually better to run the built jar:

```dockerfile
CMD ["java", "-jar", "app.jar"]
```

### 6. The file is named `DOCKERFILE`

Docker commonly looks for a file named `Dockerfile`.

Your Compose file can still use `DOCKERFILE` if you specify it:

```yaml
build:
  context: .
  dockerfile: DOCKERFILE
```

But if you only write:

```yaml
build:
  context: .
```

Compose expects `Dockerfile`, not `DOCKERFILE`.

Either rename `DOCKERFILE` to `Dockerfile`, or explicitly mention `dockerfile: DOCKERFILE` in Compose.

### 7. A single-stage image will include build tools and source code

If you build and run in the same image, the final image contains Maven cache, source files, and build tools. It works, but it is larger than needed.

For a Spring Boot app, a multi-stage Dockerfile is cleaner: one stage builds the jar, the second stage runs only the jar.

## Example corrected Dockerfile

```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY src src

RUN ./mvnw clean package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]
```

If keeping the filename as `DOCKERFILE`, use this in `compose.yaml`:

```yaml
services:
  app:
    build:
      context: .
      dockerfile: DOCKERFILE
```
