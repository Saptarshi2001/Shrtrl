package com.shrtrl.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shrtrl.DTOs.ApiErrorResponse;
import com.shrtrl.DTOs.UpdateLongUrl;
import com.shrtrl.DTOs.UpdateUrlResponse;
import com.shrtrl.DTOs.UrlCreateRequest;
import com.shrtrl.DTOs.UrlCreateResponse;
import com.shrtrl.models.Event;
import com.shrtrl.services.UrlCacheService;
import com.shrtrl.services.UrlService;
import com.shrtrl.services.GeoLocationService;
import com.shrtrl.interfaces.urls.UrlShortenResult;
import com.shrtrl.interfaces.urls.UpdateUrl;
import com.shrtrl.interfaces.urls.DeleteUrl;
import com.shrtrl.interfaces.urls.Resolveurl.shortcodeFound;
import com.shrtrl.interfaces.urls.Resolveurl.shortcodeNotFound;
import com.shrtrl.transport.EventStorage;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/urls")
public class UrlController {

    private static final Logger LOGGER = LoggerFactory.getLogger(UrlController.class);
    
    //private final TransportLayer transport;
    private final UrlService urlService;
    private final UrlCacheService urlCacheService;
    private final EventStorage eventstorage;
    private final GeoLocationService geoLocationService;

    public UrlController(UrlService urlService, UrlCacheService urlCacheService,
            EventStorage eventstorage, GeoLocationService geoLocationService) {
        this.urlService = urlService;
        this.urlCacheService = urlCacheService;
        this.eventstorage=eventstorage;
        this.geoLocationService = geoLocationService;
        
    }

    
    @PostMapping
    public ResponseEntity<?> shortenUrl(
            HttpSession session,
            @Valid @RequestBody UrlCreateRequest request) {
        UUID userId = authenticatedUserId(session);
        if (userId == null) {
            return unauthorized();
        }

        UrlShortenResult result = urlService.shortenurl(request, userId);
        switch (result) {
            case UrlShortenResult.UrlShortened shortened -> {
                UrlCreateResponse response = shortened.response();
                urlCacheService.set(response.shortCode(), response.longUrl());
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
            }
            case UrlShortenResult.NoUserFound notfound -> {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        new ApiErrorResponse(404, "Not Found", "No user found"));
            }
            case UrlShortenResult.NoGroupFound notfound -> {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        new ApiErrorResponse(404, "Not Found", "group does not exist"));
            }
            case UrlShortenResult.DatabaseFailure dbfailure -> {
                LOGGER.error("Could not shorten URL", dbfailure.cause());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                        new ApiErrorResponse(500, "Internal Server Error", "Database operation failed"));
            }
            case UrlShortenResult.RuntimeFailure runtimefailure -> {
                LOGGER.error("Could not shorten URL", runtimefailure.cause());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                        new ApiErrorResponse(500, "Internal Server Error", "Url could not be shortened"));
            }
        }
    }

    @GetMapping("/{shortcode}")
    public ResponseEntity<?> resolveUrl(HttpServletRequest request, @PathVariable("shortcode") String shortcode) {
        // The current cache stores only the destination; the event also needs its owner.
        var result = urlService.findByShortCode(shortcode);
        switch (result) {
            case shortcodeNotFound notfound -> {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        new ApiErrorResponse(404, "Not Found", "url does not exist"));
            }
            case shortcodeFound found -> {
                var url = found.url();
                String longUrl = urlCacheService.get(shortcode);
                if (longUrl == null) {
                    longUrl = url.getlongUrl();
                    urlCacheService.set(shortcode, longUrl);
                }

                String ip = request.getRemoteAddr();
                var location = geoLocationService.lookup(ip);
                Event event = new Event(
                        UUID.randomUUID(),
                        url.getUser().getUserId(),
                        shortcode,
                        Instant.now(),
                        location.country(),
                        location.state(),
                        ip,
                        request.getHeader("User-Agent"),
                        request.getHeader("Referer"));
                eventstorage.insertEvent(event);

                return ResponseEntity.status(HttpStatus.FOUND)
                        .location(URI.create(longUrl))
                        .build();
            }
        }
    }

    @PutMapping("/{shortcode}")
    public ResponseEntity<?> updateLongUrl(
            HttpSession session,
            @Valid @RequestBody UpdateLongUrl request,
            @PathVariable String shortcode) {
        UUID userId = authenticatedUserId(session);
        if (userId == null) {
            return unauthorized();
        }

        UpdateUrl result = urlService.updateLongurl(shortcode, request, userId);
        switch (result) {
            case UpdateUrl.UrlUpdated updated -> {
                urlCacheService.set(shortcode, request.longUrl());
                return ResponseEntity.ok(new UpdateUrlResponse(
                        request.longUrl(), shortcode, "Url updated"));
            }
            case UpdateUrl.NoShortCodeFound notfound -> {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        new ApiErrorResponse(404, "Not Found", "url does not exist"));
            }
            case UpdateUrl.DatabaseFailure dbfailure -> {
                LOGGER.error("Could not update URL", dbfailure.cause());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                        new ApiErrorResponse(500, "Internal Server Error", "Database operation failed"));
            }
            case UpdateUrl.RuntimeFailure runtimefailure -> {
                LOGGER.error("Could not update URL", runtimefailure.cause());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                        new ApiErrorResponse(500, "Internal Server Error", "Url could not be updated"));
            }
        }
    }

    @DeleteMapping("/{shortcode}")
    public ResponseEntity<?> deleteUrl(
            HttpSession session,
            @PathVariable String shortcode) {
        UUID userId = authenticatedUserId(session);
        if (userId == null) {
            return unauthorized();
        }

        DeleteUrl result = urlService.deleteurl(shortcode, userId);
        switch (result) {
            case DeleteUrl.UrlDeleted deleted -> {
                urlCacheService.delete(shortcode);
                return ResponseEntity.status(HttpStatus.OK).body("Url deleted successfully");
            }
            case DeleteUrl.NoShortCodeFound notfound -> {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        new ApiErrorResponse(404, "Not Found", "url does not exist"));
            }
            case DeleteUrl.DatabaseFailure dbfailure -> {
                LOGGER.error("Could not delete URL", dbfailure.cause());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                        new ApiErrorResponse(500, "Internal Server Error", "Database operation failed"));
            }
            case DeleteUrl.RuntimeFailure runtimefailure -> {
                LOGGER.error("Could not delete URL", runtimefailure.cause());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                        new ApiErrorResponse(500, "Internal Server Error", "Url could not be deleted"));
            }
        }
    }

    private UUID authenticatedUserId(HttpSession session) {
        Object value = session.getAttribute("USER_ID");
        return value instanceof UUID userId ? userId : null;
    }

    private ResponseEntity<ApiErrorResponse> unauthorized() {
        HttpStatus status = HttpStatus.UNAUTHORIZED;
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(
                        status.value(),
                        "Unauthorized",
                        "login is required"
                ));
    }

    
    
}
