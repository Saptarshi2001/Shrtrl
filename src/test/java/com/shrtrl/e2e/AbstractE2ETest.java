package com.shrtrl.e2e;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Real HTTP tests, isolated from every developer/production datasource. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
abstract class AbstractE2ETest {
    static {
        try {
            java.nio.file.Files.createDirectories(java.nio.file.Path.of(
                    System.getProperty("jdk.net.unixdomain.tmpdir", System.getProperty("java.io.tmpdir"))));
        } catch (java.io.IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("shrtrl_e2e")
            .withUsername("shrtrl_e2e")
            .withPassword("e2e_only_password")
            .withLabel("com.shrtrl.test-suite", "e2e")
            .withReuse(false);

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withCommand("redis-server", "--save", "", "--appendonly", "no")
            .withLabel("com.shrtrl.test-suite", "e2e")
            .withReuse(false);

    @Container
    static final GenericContainer<?> CLICKHOUSE = new GenericContainer<>(DockerImageName.parse("clickhouse/clickhouse-server:24"))
            .withExposedPorts(8123)
            .withEnv("CLICKHOUSE_DB", "shrtrl_e2e")
            .withEnv("CLICKHOUSE_USER", "shrtrl_e2e")
            .withEnv("CLICKHOUSE_PASSWORD", "e2e_only_password")
            .withEnv("CLICKHOUSE_DEFAULT_ACCESS_MANAGEMENT", "1")
            .withCopyFileToContainer(MountableFile.forClasspathResource("e2e/clickhouse-init.sql"),
                    "/docker-entrypoint-initdb.d/001_create_events.sql")
            .waitingFor(Wait.forHttp("/ping").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(2)))
            .withLabel("com.shrtrl.test-suite", "e2e")
            .withReuse(false);

    @DynamicPropertySource
    static void testDatabases(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("spring.datasource.hikari.maximum-pool-size", () -> 4);
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        properties.add("spring.flyway.enabled", () -> true);
        properties.add("app.redis.uri", () -> "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
        properties.add("clickhouse.endpoint", () -> "http://" + CLICKHOUSE.getHost() + ":" + CLICKHOUSE.getMappedPort(8123));
        properties.add("clickhouse.username", () -> "shrtrl_e2e");
        properties.add("clickhouse.password", () -> "e2e_only_password");
        properties.add("clickhouse.database", () -> "shrtrl_e2e");
        properties.add("app.analytics.transport", () -> "postgres");
    }

    @AfterAll
    static void removeTestContainers() {
        // Idempotent alongside Testcontainers' extension/Ryuk cleanup, including failures.
        try {
            CLICKHOUSE.stop();
        } finally {
            try {
                REDIS.stop();
            } finally {
                POSTGRES.stop();
            }
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    protected JdbcTemplate jdbc;

    protected Browser newBrowser() {
        return new Browser(port);
    }

    protected int groupId(String email, String name) {
        return jdbc.queryForObject("SELECT g.groupid FROM groups g JOIN users u ON u.userid = g.userid "
                + "WHERE u.email = ? AND g.groupname = ?", Integer.class, email, name);
    }

    protected static final class Browser {
        private final int port;
        private final HttpClient client;

        Browser(int port) {
            this.port = port;
            CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
            client = HttpClient.newBuilder().cookieHandler(cookies)
                    .followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(10)).build();
        }

        public Reply register(String username, String email, String password) throws Exception {
            return request("POST", "/api/v1/auth/register", Map.of("username", username, "email", email, "password", password));
        }

        public Reply login(String email, String password) throws Exception {
            return request("POST", "/api/v1/auth/login", Map.of("email", email, "password", password));
        }

        public void registerAndLogin(String username, String email, String password) throws Exception {
            Reply registration = register(username, email, password);
            assertEquals(201, registration.status(), registration.body());
            Reply login = login(email, password);
            assertEquals(200, login.status(), login.body());
        }

        public Reply request(String method, String path, Object body) throws Exception {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .timeout(Duration.ofSeconds(30)).header("Accept", "application/json");
            if (!method.equals("GET") && !method.equals("HEAD") && !method.equals("OPTIONS")) {
                Reply csrf = request("GET", "/api/v1/auth/csrf", null);
                assertEquals(200, csrf.status(), csrf.body());
                request.header("X-XSRF-TOKEN", (String) csrf.json().get("token"));
            }
            if (body != null) {
                request.header("Content-Type", "application/json");
            }
            String payload = body == null ? "" : JSON.writeValueAsString(body);
            request.method(method, HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
            return new Reply(client.send(request.build(), HttpResponse.BodyHandlers.ofString()));
        }
    }

    protected static final class Reply {
        private final HttpResponse<String> response;

        Reply(HttpResponse<String> response) { this.response = response; }
        public int status() { return response.statusCode(); }
        public String body() { return response.body(); }
        public String header(String name) { return response.headers().firstValue(name).orElse(null); }
        public Map<String, Object> json() throws Exception {
            return JSON.readValue(body(), new TypeReference<Map<String, Object>>() {});
        }
    }
}
