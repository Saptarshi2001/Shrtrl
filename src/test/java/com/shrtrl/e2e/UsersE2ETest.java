package com.shrtrl.e2e;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real HTTP/security/service/database stack, without mocked users. */
class UsersE2ETest extends AbstractE2ETest {
    private static final String PASSWORD = "correct horse battery staple";
    private static final Pattern TOKEN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");

    @LocalServerPort
    private int port;

    @Test
    void profileUpdateAndDeletionPersistAcrossRequestsAndInvalidateTheSession() throws Exception {
        Account account = registerAndLogin();
        assertProfile(account.browser(), account.name(), account.email());

        String updatedName = uniqueName("edited");
        String updatedEmail = updatedName + "@example.com";
        assertStatus(200, account.browser().mutate("PUT", "/api/v1/account",
                userJson(updatedName, updatedEmail)));
        assertProfile(account.browser(), updatedName, updatedEmail);

        // A new browser authenticates against the persisted updated account.
        Browser secondSession = new Browser();
        assertStatus(200, secondSession.mutate("POST", "/api/v1/auth/login",
                loginJson(updatedEmail)));
        assertProfile(secondSession, updatedName, updatedEmail);

        assertStatus(200, account.browser().mutate("DELETE", "/api/v1/account", null));
        assertAuthenticationRequired(account.browser().get("/api/v1/account"));
        assertStatus(401, new Browser().mutate("POST", "/api/v1/auth/login", loginJson(updatedEmail)));
    }

    @Test
    void updatingAndDeletingOneAccountNeverChangesAnotherAccount() throws Exception {
        Account alice = registerAndLogin();
        Account bob = registerAndLogin();
        String updatedName = uniqueName("changed");
        String updatedEmail = updatedName + "@example.com";

        assertStatus(200, alice.browser().mutate("PUT", "/api/v1/account",
                userJson(updatedName, updatedEmail)));
        assertProfile(bob.browser(), bob.name(), bob.email());

        assertStatus(200, alice.browser().mutate("DELETE", "/api/v1/account", null));
        assertProfile(bob.browser(), bob.name(), bob.email());
    }

    @Test
    void duplicateUsernameOrEmailIsAConflictAndTheOriginalProfileIsPreserved() throws Exception {
        Account alice = registerAndLogin();
        Account bob = registerAndLogin();

        assertStatus(409, bob.browser().mutate("PUT", "/api/v1/account",
                userJson(alice.name(), bob.email())));
        assertProfile(bob.browser(), bob.name(), bob.email());

        assertStatus(409, bob.browser().mutate("PUT", "/api/v1/account",
                userJson(bob.name(), alice.email())));
        assertProfile(bob.browser(), bob.name(), bob.email());
        assertProfile(alice.browser(), alice.name(), alice.email());
    }

    @Test
    void invalidUpdateAndMissingCsrfTokenCannotChangeTheProfile() throws Exception {
        Account account = registerAndLogin();
        assertStatus(400, account.browser().mutate("PUT", "/api/v1/account",
                userJson("", account.email())));
        assertProfile(account.browser(), account.name(), account.email());

        assertStatus(403, account.browser().send("PUT", "/api/v1/account",
                userJson(uniqueName("csrf"), account.email()), null));
        assertProfile(account.browser(), account.name(), account.email());
    }

    @Test
    void anonymousRequestsCannotReadUpdateOrDeleteAnAccount() throws Exception {
        Browser anonymous = new Browser();
        assertAuthenticationRequired(anonymous.get("/api/v1/account"));
        assertAuthenticationRequired(anonymous.mutate("PUT", "/api/v1/account",
                userJson(uniqueName("guest"), "guestperson@example.com")));
        assertAuthenticationRequired(anonymous.mutate("DELETE", "/api/v1/account", null));
    }

    private Account registerAndLogin() throws Exception {
        String name = uniqueName("user");
        String email = name + "@example.com";
        Browser browser = new Browser();
        String registration = "{\"username\":\"" + name + "\",\"email\":\"" + email
                + "\",\"password\":\"" + PASSWORD + "\"}";
        assertStatus(201, browser.mutate("POST", "/api/v1/auth/register", registration));
        assertStatus(200, browser.mutate("POST", "/api/v1/auth/login", loginJson(email)));
        return new Account(browser, name, email);
    }

    private void assertProfile(Browser browser, String name, String email) throws Exception {
        HttpResponse<String> response = browser.get("/api/v1/account");
        assertStatus(200, response);
        assertTrue(response.body().contains("\"userName\":\"" + name + "\""), response.body());
        assertTrue(response.body().contains("\"email\":\"" + email + "\""), response.body());
        assertFalse(response.body().contains("password"), "Profile must not expose password data");
    }

    private static String uniqueName(String prefix) {
        String hexadecimal = UUID.randomUUID().toString().replace("-", "");
        StringBuilder name = new StringBuilder(prefix);
        for (int index = 0; index < 10; index++) {
            name.append((char) ('a' + Character.digit(hexadecimal.charAt(index), 16)));
        }
        return name.toString();
    }

    private static String userJson(String name, String email) {
        return "{\"username\":\"" + name + "\",\"email\":\"" + email + "\"}";
    }

    private static String loginJson(String email) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}";
    }

    private static void assertStatus(int expected, HttpResponse<String> response) {
        assertEquals(expected, response.statusCode(), response.body());
    }

    private static void assertAuthenticationRequired(HttpResponse<String> response) {
        assertTrue(response.statusCode() == 401 || response.statusCode() == 403,
                "Expected authentication rejection, got " + response.statusCode() + ": " + response.body());
    }

    private record Account(Browser browser, String name, String email) {}

    private final class Browser {
        private final HttpClient client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> get(String path) throws Exception {
            return send("GET", path, null, null);
        }

        HttpResponse<String> mutate(String method, String path, String body) throws Exception {
            HttpResponse<String> csrf = get("/api/v1/auth/csrf");
            assertStatus(200, csrf);
            Matcher token = TOKEN.matcher(csrf.body());
            assertTrue(token.find(), "Missing CSRF token: " + csrf.body());
            return send(method, path, body, token.group(1));
        }

        HttpResponse<String> send(String method, String path, String body, String csrf) throws Exception {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json");
            if (csrf != null) request.header("X-XSRF-TOKEN", csrf);
            if (body != null) request.header("Content-Type", "application/json");
            request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body));
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
