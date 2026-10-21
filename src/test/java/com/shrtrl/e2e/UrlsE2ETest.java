package com.shrtrl.e2e;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.lettuce.core.api.sync.RedisCommands;

import static org.junit.jupiter.api.Assertions.*;

class UrlsE2ETest extends AbstractE2ETest {
    @Autowired
    private RedisCommands<String, String> redis;

    @Test
    void ungroupedUrlSurvivesDatabaseFallbackAndItsCacheTracksUpdatesAndDeletion() throws Exception {
        Account owner = account();
        Browser visitor = newBrowser();
        String code = shorten(owner.browser(), Map.of("longUrl", "https://ex.com/a"));

        assertNull(jdbc.queryForObject("SELECT groupid FROM urls WHERE shortcode = ?", Integer.class, code));
        assertRedirect(visitor, code, "https://ex.com/a");

        // Force a real database lookup, then verify that resolution repopulates Redis.
        redis.del("url:" + code);
        assertRedirect(visitor, code, "https://ex.com/a");
        assertEquals("https://ex.com/a", redis.get("url:" + code));

        Reply updated = owner.browser().request("PUT", "/api/v1/urls/" + code,
                Map.of("longUrl", "https://ex.com/b"));
        assertEquals(200, updated.status(), updated.body());
        assertRedirect(visitor, code, "https://ex.com/b");
        assertEquals("https://ex.com/b", redis.get("url:" + code));

        Reply deleted = owner.browser().request("DELETE", "/api/v1/urls/" + code, null);
        assertEquals(200, deleted.status(), deleted.body());
        Reply missing = visitor.request("GET", "/api/v1/urls/" + code, null);
        assertEquals(404, missing.status(), missing.body());
        assertEquals(404, ((Number) missing.json().get("statusCode")).intValue());
        assertNull(redis.get("url:" + code));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM urls WHERE shortcode = ?", Long.class, code));
    }

    @Test
    void ownedGroupContainsItsUrlAndDeletingTheGroupDoesNotDeleteTheShortUrl() throws Exception {
        Account owner = account();
        Reply created = owner.browser().request("POST", "/api/v1/groups", Map.of("groupName", "worklinks"));
        assertEquals(201, created.status(), created.body());
        int groupId = groupId(owner.email(), "worklinks");
        String code = shorten(owner.browser(), Map.of("longUrl", "https://ex.com/c", "groupId", groupId));

        assertEquals(groupId, jdbc.queryForObject("SELECT groupid FROM urls WHERE shortcode = ?", Integer.class, code));
        Reply details = owner.browser().request("GET", "/api/v1/groups/" + groupId, null);
        assertEquals(200, details.status(), details.body());
        assertEquals(groupId, ((Number) details.json().get("groupId")).intValue());
        assertEquals("worklinks", details.json().get("groupName"));
        List<?> members = (List<?>) details.json().get("urls");
        assertTrue(members.stream().anyMatch(value -> value instanceof Map<?, ?> member
                && code.equals(member.get("shortCode"))
                && "https://ex.com/c".equals(member.get("longUrl"))), details.body());

        Reply deletedGroup = owner.browser().request("DELETE", "/api/v1/groups/" + groupId, null);
        assertEquals(200, deletedGroup.status(), deletedGroup.body());
        assertNull(jdbc.queryForObject("SELECT groupid FROM urls WHERE shortcode = ?", Integer.class, code));
        assertRedirect(newBrowser(), code, "https://ex.com/c");
    }

    @Test
    void missingAndAnotherUsersGroupsCannotReceiveAnOwnedUrl() throws Exception {
        Account owner = account();
        Account other = account();
        Reply created = other.browser().request("POST", "/api/v1/groups", Map.of("groupName", "otherlinks"));
        assertEquals(201, created.status(), created.body());
        int foreignGroupId = groupId(other.email(), "otherlinks");

        for (int selectedId : new int[] {foreignGroupId, Integer.MAX_VALUE}) {
            Reply denied = owner.browser().request("POST", "/api/v1/urls",
                    Map.of("longUrl", "https://ex.com/d", "groupId", selectedId));
            assertEquals(404, denied.status(), denied.body());
            assertEquals(404, ((Number) denied.json().get("statusCode")).intValue());
        }
        assertEquals(0L, ownedUrlCount(owner.email()));
    }

    @Test
    void anotherUserCannotUpdateOrDeleteTheOwnersShortUrl() throws Exception {
        Account owner = account();
        Account other = account();
        String code = shorten(owner.browser(), Map.of("longUrl", "https://ex.com/e"));

        Reply update = other.browser().request("PUT", "/api/v1/urls/" + code,
                Map.of("longUrl", "https://ex.com/f"));
        assertEquals(404, update.status(), update.body());
        Reply deletion = other.browser().request("DELETE", "/api/v1/urls/" + code, null);
        assertEquals(404, deletion.status(), deletion.body());
        assertRedirect(newBrowser(), code, "https://ex.com/e");
        assertEquals("https://ex.com/e", jdbc.queryForObject(
                "SELECT longurl FROM urls WHERE shortcode = ?", String.class, code));
    }

    @Test
    void shorteningRequiresAuthenticationAndRejectsInvalidInput() throws Exception {
        Reply unauthenticated = newBrowser().request("POST", "/api/v1/urls",
                Map.of("longUrl", "https://ex.com/g"));
        assertTrue(unauthenticated.status() == 401 || unauthenticated.status() == 403,
                unauthenticated.body());

        Account owner = account();
        Reply invalidUrl = owner.browser().request("POST", "/api/v1/urls", Map.of("longUrl", "ftp://ex.com/a"));
        assertEquals(400, invalidUrl.status(), invalidUrl.body());
        Reply invalidGroup = owner.browser().request("POST", "/api/v1/urls",
                Map.of("longUrl", "https://ex.com/g", "groupId", 0));
        assertEquals(400, invalidGroup.status(), invalidGroup.body());
        assertEquals(0L, ownedUrlCount(owner.email()));
    }

    private String shorten(Browser browser, Map<String, Object> request) throws Exception {
        Reply response = browser.request("POST", "/api/v1/urls", request);
        assertEquals(201, response.status(), response.body());
        String code = (String) response.json().get("shortCode");
        assertNotNull(code);
        assertFalse(code.isBlank());
        return code;
    }

    private void assertRedirect(Browser browser, String code, String destination) throws Exception {
        Reply response = browser.request("GET", "/api/v1/urls/" + code, null);
        assertEquals(302, response.status(), response.body());
        assertEquals(destination, response.header("Location"));
    }

    private long ownedUrlCount(String email) {
        return jdbc.queryForObject("SELECT count(*) FROM urls WHERE userid = "
                + "(SELECT userid FROM users WHERE email = ?)", Long.class, email);
    }

    private Account account() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                .replace('0', 'a').replace('1', 'b').replace('2', 'c').replace('3', 'd').replace('4', 'e')
                .replace('5', 'f').replace('6', 'g').replace('7', 'h').replace('8', 'i').replace('9', 'j');
        String username = "url" + suffix;
        String email = username + "@ex.com";
        Browser browser = newBrowser();
        browser.registerAndLogin(username, email, "Password123!");
        return new Account(browser, email);
    }

    private record Account(Browser browser, String email) {}
}
