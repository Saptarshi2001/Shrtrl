package com.shrtrl.services;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shrtrl.DTOs.UpdateLongUrl;
import com.shrtrl.DTOs.UrlCreateRequest;
import com.shrtrl.DTOs.UrlCreateResponse;
import com.shrtrl.models.Urls;
import com.shrtrl.models.Users;
import com.shrtrl.queries.UrlQueries;
import com.shrtrl.queries.UserQueries;

@Service
public class UrlService {
    private final UrlQueries urldbAccess;
    private final UserQueries userdbAccess;

    public UrlService(UrlQueries urldbAccess, UserQueries userdbAccess) {
        this.userdbAccess = userdbAccess;
        this.urldbAccess = urldbAccess;
    }

    public Optional<Urls> findByShortCode(String shortCode) {
        return urldbAccess.findByShortCode(shortCode);
    } // a type for inability to find short code 

    private String shorten(int id) {
        String chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder result = new StringBuilder();
        do {
            result.append(chars.charAt(id % 62));
            id /= 62;
        } while (id > 0);

        return result.reverse().toString();
    }

    @Transactional
    public boolean updateLongurl(String shortcode, UpdateLongUrl request, UUID userId) {
        Optional<Urls> containerurls = urldbAccess.findByShortCodeAndUser_UserId(
                shortcode,
                userId
        );
        if (containerurls.isEmpty()) {
            return false;
        }

        Urls url = containerurls.get();
        url.setlongUrl(request.longUrl());
        return true;
    }// a type for no shortcode,a type for db failure, a type for updateurl

    @Transactional
    public boolean deleteurl(String shortcode, UUID userId) {
        Optional<Urls> url = urldbAccess.findByShortCodeAndUser_UserId(
                shortcode,
                userId
        );
        if (url.isEmpty()) {
            return false;
        }

        urldbAccess.delete(url.get());
        return true;
    }// a type for no shortcode,a type for db failure, a type for deleteurl

    @Transactional
    public UrlCreateResponse shortenurl(UrlCreateRequest request, UUID userId) {
        Users user = userdbAccess.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String longUrl = request.longUrl();
        OffsetDateTime createdAt = OffsetDateTime.now();

        Urls url = new Urls(longUrl, null, createdAt);
        url.setUser(user);
        urldbAccess.saveAndFlush(url);

        String shortCode = shorten(url.getUrlId());
        url.setShortCode(shortCode);
        urldbAccess.save(url);

        return new UrlCreateResponse(longUrl, shortCode, createdAt, true);
    } // a type for no id,a type for db failure, a type for updateurl
}
