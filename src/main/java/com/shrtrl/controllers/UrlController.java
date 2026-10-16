package com.shrtrl.controllers;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataAccessException;
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
import com.shrtrl.models.Urls;
import com.shrtrl.services.UrlCacheService;
import com.shrtrl.services.UrlService;
import com.shrtrl.analytics.Transport.EventStorage;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/urls")
public class UrlController {
    
    //private final TransportLayer transport;
    private final UrlService urlService;
    private final UrlCacheService urlCacheService;
    private final EventStorage eventstorage;

    public UrlController(UrlService urlService, UrlCacheService urlCacheService,EventStorage eventstorage) {
        this.urlService = urlService;
        this.urlCacheService = urlCacheService;
        this.eventstorage=eventstorage;
        
    }

    
    @PostMapping
    public ResponseEntity<?> shortenUrl(
            HttpSession session,
            @Valid @RequestBody UrlCreateRequest request) {
        UUID userId = authenticatedUserId(session);
        if (userId == null) {
            return unauthorized();
        }

        try {
            UrlCreateResponse result = urlService.shortenurl(request, userId);
            urlCacheService.set(result.shortCode(), result.longUrl());
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (DataAccessException exception) {
            return databaseError(exception);
        } catch (RuntimeException exception) {
            return runtimeError(exception);
        }
    }

    @GetMapping("/{shortcode}")
    public ResponseEntity<?> resolveUrl(@PathVariable String shortcode) {
        try {
            //eventstorage.insertEvent(event);
            String longUrl = urlCacheService.get(shortcode);

            if (longUrl == null) {
                Optional<Urls> url = urlService.findByShortCode(shortcode);
                if (url.isEmpty()) {
                    return notFound("url does not exist");
                }

                longUrl = url.get().getlongUrl();
                urlCacheService.set(shortcode, longUrl);
            }

            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(longUrl))
                    .build();
        } catch (DataAccessException exception) {
            return databaseError(exception);
        } catch (RuntimeException exception) {
            return runtimeError(exception);
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

        try {
            if (!urlService.updateLongurl(shortcode, request, userId)) {
                return notFound("url does not exist");
            }

            urlCacheService.set(shortcode, request.longUrl());
            return ResponseEntity.ok(new UpdateUrlResponse(
                    request.longUrl(),
                    shortcode,
                    "Url updated"
            ));
        } catch (DataAccessException exception) {
            return databaseError(exception);
        } catch (RuntimeException exception) {
            return runtimeError(exception);
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

        try {
            if (!urlService.deleteurl(shortcode, userId)) {
                return notFound("url does not exist");
            }

            urlCacheService.delete(shortcode);
            return ResponseEntity.ok("Url deleted successfully");
        } catch (DataAccessException exception) {
            return databaseError(exception);
        } catch (RuntimeException exception) {
            return runtimeError(exception);
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

    private ResponseEntity<ApiErrorResponse> notFound(String message) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(status.value(), "Not Found", message));
    }

    private ResponseEntity<ApiErrorResponse> databaseError(DataAccessException exception) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(
                        status.value(),
                        "DataAccessException",
                        exception.getMessage()
                ));
    }

    private ResponseEntity<ApiErrorResponse> runtimeError(RuntimeException exception) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(
                        status.value(),
                        "RuntimeException",
                        exception.getMessage()
                ));
    }
}
