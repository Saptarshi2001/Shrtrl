package com.shrtrl.e2e;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** End-to-end group ownership and optional URL membership over real HTTP. */
class GroupsE2ETest extends AbstractE2ETest {
    private static final String PASSWORD = "correct horse battery staple";

    @Test
    void detailsContainOnlyTheGroupsUrlsAndEmptyGroupsHaveAnEmptyList() throws Exception {
        Account owner = account();
        int selected = createGroup(owner, "workgroup");
        int empty = createGroup(owner, "emptygroup");

        Reply initial = owner.browser().request("GET", "/api/v1/groups/" + selected, null);
        assertStatus(200, initial);
        assertEquals(selected, ((Number) initial.json().get("groupId")).intValue());
        assertEquals("workgroup", initial.json().get("groupName"));
        assertEquals(List.of(), initial.json().get("urls"));

        Reply grouped = owner.browser().request("POST", "/api/v1/urls",
                Map.of("longUrl", "https://ex.com/a", "groupId", selected));
        assertStatus(201, grouped);
        String groupedCode = (String) grouped.json().get("shortCode");
        assertStatus(201, owner.browser().request("POST", "/api/v1/urls",
                Map.of("longUrl", "https://ex.com/b")));

        Reply populated = owner.browser().request("GET", "/api/v1/groups/" + selected, null);
        assertStatus(200, populated);
        List<?> links = (List<?>) populated.json().get("urls");
        assertEquals(1, links.size());
        Map<?, ?> link = (Map<?, ?>) links.get(0);
        assertEquals("https://ex.com/a", link.get("longUrl"));
        assertEquals(groupedCode, link.get("shortCode"));
        Reply other = owner.browser().request("GET", "/api/v1/groups/" + empty, null);
        assertStatus(200, other);
        assertEquals(List.of(), other.json().get("urls"));
    }

    @Test
    void foreignGroupsCannotBeReadRenamedDeletedOrUsedForShortening() throws Exception {
        Account alice = account();
        Account bob = account();
        int group = createGroup(alice, "privategroup");

        assertStatus(404, bob.browser().request("GET", "/api/v1/groups/" + group, null));
        assertStatus(404, bob.browser().request("PUT", "/api/v1/groups/" + group,
                Map.of("groupName", "renamedgroup")));
        assertStatus(404, bob.browser().request("DELETE", "/api/v1/groups/" + group, null));
        Reply rejected = bob.browser().request("POST", "/api/v1/urls",
                Map.of("longUrl", "https://ex.com", "groupId", group));
        assertStatus(404, rejected);
        assertEquals(404, ((Number) rejected.json().get("statusCode")).intValue());
        Reply original = alice.browser().request("GET", "/api/v1/groups/" + group, null);
        assertStatus(200, original);
        assertEquals("privategroup", original.json().get("groupName"));
        assertEquals(List.of(), original.json().get("urls"));
    }

    @Test
    void duplicateNamesConflictOnlyForTheSameOwnerAndOwnerCanRename() throws Exception {
        Account alice = account();
        Account bob = account();
        int group = createGroup(alice, "workgroup");
        Reply duplicate = alice.browser().request("POST", "/api/v1/groups",
                Map.of("groupName", "WORKGROUP"));
        assertStatus(409, duplicate);
        assertEquals(409, ((Number) duplicate.json().get("statusCode")).intValue());
        createGroup(bob, "workgroup");

        assertStatus(200, alice.browser().request("PUT", "/api/v1/groups/" + group,
                Map.of("groupName", "renamedgroup")));
        Reply renamed = alice.browser().request("GET", "/api/v1/groups/" + group, null);
        assertStatus(200, renamed);
        assertEquals("renamedgroup", renamed.json().get("groupName"));
    }

    @Test
    void deletingAGroupLeavesItsShortUrlsUsableAndUngrouped() throws Exception {
        Account owner = account();
        int group = createGroup(owner, "temporary");
        Reply created = owner.browser().request("POST", "/api/v1/urls",
                Map.of("longUrl", "https://ex.com", "groupId", group));
        assertStatus(201, created);
        String code = (String) created.json().get("shortCode");

        assertStatus(200, owner.browser().request("DELETE", "/api/v1/groups/" + group, null));
        assertStatus(404, owner.browser().request("GET", "/api/v1/groups/" + group, null));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM urls WHERE shortcode = ? AND groupid IS NULL",
                Integer.class, code));
        Reply redirect = newBrowser().request("GET", "/api/v1/urls/" + code, null);
        assertStatus(302, redirect);
        assertEquals("https://ex.com", redirect.header("Location"));
    }

    private Account account() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10)
                .replace('0', 'a').replace('1', 'b').replace('2', 'c').replace('3', 'd')
                .replace('4', 'e').replace('5', 'f').replace('6', 'g').replace('7', 'h')
                .replace('8', 'i').replace('9', 'j');
        String name = "group" + suffix;
        String email = name + "@example.com";
        Browser browser = newBrowser();
        browser.registerAndLogin(name, email, PASSWORD);
        return new Account(browser, email);
    }

    private int createGroup(Account account, String name) throws Exception {
        assertStatus(201, account.browser().request("POST", "/api/v1/groups", Map.of("groupName", name)));
        return groupId(account.email(), name);
    }

    private void assertStatus(int expected, Reply response) {
        assertEquals(expected, response.status(), response.body());
    }

    private record Account(Browser browser, String email) {}
}
