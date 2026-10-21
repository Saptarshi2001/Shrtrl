package com.shrtrl.services;

import java.util.Optional;
import java.util.UUID;

import com.shrtrl.DTOs.ApiErrorResponse;
import com.shrtrl.DTOs.UrlCreateRequest;
import com.shrtrl.transport.EventStorage;
import com.shrtrl.controllers.UrlController;
import com.shrtrl.interfaces.urls.UrlShortenResult;
import com.shrtrl.models.Groups;
import com.shrtrl.models.Urls;
import com.shrtrl.models.Users;
import com.shrtrl.queries.GroupQueries;
import com.shrtrl.queries.UrlQueries;
import com.shrtrl.queries.UserQueries;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UrlGroupAssignmentTest {
    private final UUID userId = UUID.randomUUID();
    private final UserQueries users = mock(UserQueries.class);
    private final GroupQueries groups = mock(GroupQueries.class);
    private final UrlQueries urls = mock(UrlQueries.class);
    private final Users owner = new Users("alice", "alice@example.com", "hash");
    private final UrlService service = new UrlService(urls, users, groups);
    private Urls savedUrl;

    @BeforeEach
    void setUp() {
        when(users.findById(userId)).thenReturn(Optional.of(owner));
        when(urls.saveAndFlush(any(Urls.class))).thenAnswer(invocation -> {
            savedUrl = invocation.getArgument(0);
            savedUrl.setUrlId(123);
            return savedUrl;
        });
    }

    @Test
    void createsUngroupedUrlWhenNoGroupSelected() {
        assertInstanceOf(UrlShortenResult.UrlShortened.class,
                service.shortenurl(new UrlCreateRequest("https://ex.com"), userId));
        assertNull(savedUrl.getGroup());
        assertSame(owner, savedUrl.getUser());
        verifyNoInteractions(groups);
    }

    @Test
    void assignsOnlyAnOwnedGroup() {
        Groups selectedGroup = new Groups("workgroup", owner);
        when(groups.findByGroupIdAndUser_UserId(17, userId)).thenReturn(Optional.of(selectedGroup));
        assertInstanceOf(UrlShortenResult.UrlShortened.class,
                service.shortenurl(new UrlCreateRequest("https://ex.com", 17), userId));
        assertSame(selectedGroup, savedUrl.getGroup());
        verify(groups).findByGroupIdAndUser_UserId(17, userId);
    }

    @Test
    void rejectsMissingOrForeignGroupBeforeSavingUrl() {
        // This owner-scoped lookup returns empty for both missing and foreign groups.
        when(groups.findByGroupIdAndUser_UserId(17, userId)).thenReturn(Optional.empty());
        assertInstanceOf(UrlShortenResult.NoGroupFound.class,
                service.shortenurl(new UrlCreateRequest("https://ex.com", 17), userId));
        verifyNoInteractions(urls);
    }

    @Test
    void controllerReturns404WithoutCachingWhenGroupNotFound() {
        UrlCacheService cache = mock(UrlCacheService.class);
        UrlController controller = new UrlController(service, cache, mock(EventStorage.class), mock(GeoLocationService.class));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("USER_ID", userId);
        when(groups.findByGroupIdAndUser_UserId(17, userId)).thenReturn(Optional.empty());
        var response = controller.shortenUrl(session, new UrlCreateRequest("https://ex.com", 17));
        assertEquals(404, response.getStatusCode().value());
        assertInstanceOf(ApiErrorResponse.class, response.getBody());
        verifyNoInteractions(cache, urls);
    }
}
