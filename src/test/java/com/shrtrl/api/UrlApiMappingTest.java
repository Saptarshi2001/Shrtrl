package com.shrtrl.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.shrtrl.ApiExceptionHandler;
import com.shrtrl.controllers.UrlController;
import com.shrtrl.DTOs.UpdateLongUrl;
import com.shrtrl.DTOs.UrlCreateRequest;
import com.shrtrl.DTOs.UrlCreateResponse;
import com.shrtrl.models.Urls;
import com.shrtrl.interfaces.urls.Resolveurl;
import com.shrtrl.services.UrlCacheService;
import com.shrtrl.services.UrlService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UrlApiMappingTest {

    private static final UUID USER_ID = UUID.fromString(
            "6e7b5d38-c7ea-43f9-9c80-e71c6bc9a917"
    );

    private UrlService urlService;
    private UrlCacheService urlCacheService;
    private com.shrtrl.services.GeoLocationService geoLocationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        urlService = mock(UrlService.class);
        urlCacheService = mock(UrlCacheService.class);
        geoLocationService = mock(com.shrtrl.services.GeoLocationService.class);
        when(geoLocationService.lookup(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(com.shrtrl.DTOs.GeoLocation.UNKNOWN);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new UrlController(urlService, urlCacheService,
                        mock(com.shrtrl.transport.EventStorage.class), geoLocationService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void postUrlCreatesOwnedShortUrlAndCachesIt() throws Exception {
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-07-26T10:15:30Z");
        UrlCreateRequest request = new UrlCreateRequest("https://example.com");
        UrlCreateResponse response = new UrlCreateResponse(
                request.longUrl(),
                "abc123",
                createdAt,
                true
        );
        when(urlService.shortenurl(request, USER_ID)).thenReturn(new com.shrtrl.interfaces.urls.UrlShortenResult.UrlShortened(response));

        mockMvc.perform(post("/api/v1/urls")
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"longUrl":"https://example.com"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("abc123"));

        verify(urlService).shortenurl(request, USER_ID);
        verify(urlCacheService).set("abc123", "https://example.com");
    }

    @Test
    void postUrlRequiresAuthenticatedUserSession() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"longUrl":"https://example.com"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.statusCode").value(401));
    }

    @Test
    void postUrlRejectsInvalidUrl() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"longUrl":"ftp://example.com/file"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getShortCodeRedirectsFromCacheAndLooksUpOwnerForEvent() throws Exception {
        Urls url = new Urls("https://example.com/from-cache", "abc123", OffsetDateTime.now());
        var owner = new com.shrtrl.models.Users("Owner", "owner@example.com", "password");
        owner.setUserId(USER_ID);
        url.setUser(owner);
        when(urlService.findByShortCode("abc123")).thenReturn(new Resolveurl.shortcodeFound(url));
        when(urlCacheService.get("abc123"))
                .thenReturn("https://example.com/from-cache");

        mockMvc.perform(get("/api/v1/urls/{shortcode}", "abc123"))
                .andExpect(status().isFound())
                .andExpect(header().string(
                        "Location",
                        "https://example.com/from-cache"
                ));

        verify(urlService).findByShortCode("abc123");
    }

    @Test
    void getShortCodeFallsBackToDatabaseAndRefillsCache() throws Exception {
        when(urlCacheService.get("abc123")).thenReturn(null);
        Urls url = new Urls(
                "https://example.com/from-database",
                "abc123",
                OffsetDateTime.parse("2026-07-26T10:15:30Z")
        );
        var owner = new com.shrtrl.models.Users("Owner", "owner@example.com", "password");
        owner.setUserId(USER_ID);
        url.setUser(owner);
        when(urlService.findByShortCode("abc123")).thenReturn(new Resolveurl.shortcodeFound(url));

        mockMvc.perform(get("/api/v1/urls/{shortcode}", "abc123"))
                .andExpect(status().isFound())
                .andExpect(header().string(
                        "Location",
                        "https://example.com/from-database"
                ));

        verify(urlCacheService).set(
                "abc123",
                "https://example.com/from-database"
        );
    }

    @Test
    void getShortCodeReturnsNotFoundWhenCacheAndDatabaseMiss() throws Exception {
        when(urlCacheService.get("missing")).thenReturn(null);
        when(urlService.findByShortCode("missing")).thenReturn(new Resolveurl.shortcodeNotFound());

        mockMvc.perform(get("/api/v1/urls/{shortcode}", "missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.statusCode").value(404));
    }

    @Test
    void putShortCodeUpdatesOwnedUrlAndCache() throws Exception {
        UpdateLongUrl request = new UpdateLongUrl("https://example.org");
        when(urlService.updateLongurl("abc123", request, USER_ID)).thenReturn(new com.shrtrl.interfaces.urls.UpdateUrl.UrlUpdated());

        mockMvc.perform(put("/api/v1/urls/{shortcode}", "abc123")
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"longUrl":"https://example.org"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value("abc123"));

        verify(urlService).updateLongurl("abc123", request, USER_ID);
        verify(urlCacheService).set("abc123", "https://example.org");
    }

    @Test
    void putShortCodeReturnsNotFoundWhenUserDoesNotOwnUrl() throws Exception {
        UpdateLongUrl request = new UpdateLongUrl("https://example.org");
        when(urlService.updateLongurl("abc123", request, USER_ID)).thenReturn(new com.shrtrl.interfaces.urls.UpdateUrl.NoShortCodeFound());

        mockMvc.perform(put("/api/v1/urls/{shortcode}", "abc123")
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"longUrl":"https://example.org"}
                                """))
                .andExpect(status().isNotFound());

        verify(urlCacheService, never()).set("abc123", request.longUrl());
    }

    @Test
    void deleteShortCodeDeletesOwnedUrlAndCachedValue() throws Exception {
        when(urlService.deleteurl("abc123", USER_ID)).thenReturn(new com.shrtrl.interfaces.urls.DeleteUrl.UrlDeleted());

        mockMvc.perform(delete("/api/v1/urls/{shortcode}", "abc123")
                        .session(authenticatedSession()))
                .andExpect(status().isOk());

        verify(urlService).deleteurl("abc123", USER_ID);
        verify(urlCacheService).delete("abc123");
    }

    @Test
    void deleteShortCodeReturnsNotFoundWhenUserDoesNotOwnUrl() throws Exception {
        when(urlService.deleteurl("abc123", USER_ID)).thenReturn(new com.shrtrl.interfaces.urls.DeleteUrl.NoShortCodeFound());

        mockMvc.perform(delete("/api/v1/urls/{shortcode}", "abc123")
                        .session(authenticatedSession()))
                .andExpect(status().isNotFound());

        verify(urlCacheService, never()).delete("abc123");
    }

    @Test
    void urlMappingRejectsUnsupportedPatch() throws Exception {
        mockMvc.perform(patch("/api/v1/urls/{shortcode}", "abc123"))
                .andExpect(status().isMethodNotAllowed());
    }

    private MockHttpSession authenticatedSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("USER_ID", USER_ID);
        return session;
    }
}
