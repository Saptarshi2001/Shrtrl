package com.shrtrl.api;

import java.time.OffsetDateTime;
import java.util.Optional;
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
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        urlService = mock(UrlService.class);
        urlCacheService = mock(UrlCacheService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new UrlController(urlService, urlCacheService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void postUrlCreatesOwnedShortUrlAndCachesIt() throws Exception {
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-07-26T10:15:30Z");
        UrlCreateRequest request = new UrlCreateRequest("https://example.com/long");
        UrlCreateResponse response = new UrlCreateResponse(
                request.longUrl(),
                "abc123",
                createdAt,
                true
        );
        when(urlService.shortenurl(request, USER_ID)).thenReturn(response);

        mockMvc.perform(post("/api/v1/urls")
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"longUrl":"https://example.com/long"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("abc123"));

        verify(urlService).shortenurl(request, USER_ID);
        verify(urlCacheService).set("abc123", "https://example.com/long");
    }

    @Test
    void postUrlRequiresAuthenticatedUserSession() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"longUrl":"https://example.com/long"}
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
    void getShortCodeRedirectsFromCacheWithoutQueryingDatabase() throws Exception {
        when(urlCacheService.get("abc123"))
                .thenReturn("https://example.com/from-cache");

        mockMvc.perform(get("/api/v1/urls/{shortcode}", "abc123"))
                .andExpect(status().isFound())
                .andExpect(header().string(
                        "Location",
                        "https://example.com/from-cache"
                ));

        verify(urlService, never()).findByShortCode("abc123");
    }

    @Test
    void getShortCodeFallsBackToDatabaseAndRefillsCache() throws Exception {
        when(urlCacheService.get("abc123")).thenReturn(null);
        Urls url = new Urls(
                "https://example.com/from-database",
                "abc123",
                OffsetDateTime.parse("2026-07-26T10:15:30Z")
        );
        when(urlService.findByShortCode("abc123")).thenReturn(Optional.of(url));

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
        when(urlService.findByShortCode("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/urls/{shortcode}", "missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.statusCode").value(404));
    }

    @Test
    void putShortCodeUpdatesOwnedUrlAndCache() throws Exception {
        UpdateLongUrl request = new UpdateLongUrl("https://example.com/new");
        when(urlService.updateLongurl("abc123", request, USER_ID)).thenReturn(true);

        mockMvc.perform(put("/api/v1/urls/{shortcode}", "abc123")
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"longUrl":"https://example.com/new"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value("abc123"));

        verify(urlService).updateLongurl("abc123", request, USER_ID);
        verify(urlCacheService).set("abc123", "https://example.com/new");
    }

    @Test
    void putShortCodeReturnsNotFoundWhenUserDoesNotOwnUrl() throws Exception {
        UpdateLongUrl request = new UpdateLongUrl("https://example.com/new");
        when(urlService.updateLongurl("abc123", request, USER_ID)).thenReturn(false);

        mockMvc.perform(put("/api/v1/urls/{shortcode}", "abc123")
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"longUrl":"https://example.com/new"}
                                """))
                .andExpect(status().isNotFound());

        verify(urlCacheService, never()).set("abc123", request.longUrl());
    }

    @Test
    void deleteShortCodeDeletesOwnedUrlAndCachedValue() throws Exception {
        when(urlService.deleteurl("abc123", USER_ID)).thenReturn(true);

        mockMvc.perform(delete("/api/v1/urls/{shortcode}", "abc123")
                        .session(authenticatedSession()))
                .andExpect(status().isOk());

        verify(urlService).deleteurl("abc123", USER_ID);
        verify(urlCacheService).delete("abc123");
    }

    @Test
    void deleteShortCodeReturnsNotFoundWhenUserDoesNotOwnUrl() throws Exception {
        when(urlService.deleteurl("abc123", USER_ID)).thenReturn(false);

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
