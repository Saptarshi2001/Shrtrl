package com.shrtrl.services;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.shrtrl.DTOs.UpdateLongUrl;
import com.shrtrl.DTOs.UrlCreateRequest;
import com.shrtrl.DTOs.UrlCreateResponse;
import com.shrtrl.interfaces.urls.DeleteUrl;
import com.shrtrl.interfaces.urls.Resolveurl;
import com.shrtrl.interfaces.urls.UpdateUrl;
import com.shrtrl.interfaces.urls.UrlShortenResult;
import com.shrtrl.models.Urls;
import com.shrtrl.models.Users;
import com.shrtrl.models.Groups;
import com.shrtrl.queries.GroupQueries;
import com.shrtrl.queries.UrlQueries;
import com.shrtrl.queries.UserQueries;

@Service
public class UrlService {
    private final UrlQueries urldbAccess;
    private final UserQueries userdbAccess;
    private final GroupQueries groupdbaccess;
    public UrlService(UrlQueries urldbAccess, UserQueries userdbAccess, GroupQueries groupdbaccess) {
        this.userdbAccess = userdbAccess;
        this.urldbAccess = urldbAccess;
        this.groupdbaccess = groupdbaccess;
    }

    public Resolveurl findByShortCode(String shortCode) {

        return urldbAccess.findByShortCode(shortCode)
                .<Resolveurl>map(Resolveurl.shortcodeFound::new)
                .orElseGet(Resolveurl.shortcodeNotFound::new);
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
    public UpdateUrl updateLongurl(String shortcode, UpdateLongUrl request, UUID userId) {
        try {
            Optional<Urls> containerurls = urldbAccess.findByShortCodeAndUser_UserId(
                    shortcode,
                    userId
            );
            if (containerurls.isEmpty()) {
                return new UpdateUrl.NoShortCodeFound();
            }
    
            Urls url = containerurls.get();
            url.setlongUrl(request.longUrl());
            urldbAccess.flush();
            return new UpdateUrl.UrlUpdated();
        
        } catch (DataAccessException | TransactionException exception) {
            markTransactionForRollback();
            return new UpdateUrl.DatabaseFailure(exception);
        } catch (RuntimeException exception) {
            markTransactionForRollback();
            return new UpdateUrl.RuntimeFailure(exception);
        }
    }

    @Transactional
    public DeleteUrl deleteurl(String shortcode, UUID userId) {
        try {
            Optional<Urls> url = urldbAccess.findByShortCodeAndUser_UserId(
                    shortcode,
                    userId
            );
            if (url.isEmpty()) {
                return new DeleteUrl.NoShortCodeFound();
            }
    
            urldbAccess.delete(url.get());
            urldbAccess.flush();
            return new DeleteUrl.UrlDeleted();
        
        } catch (DataAccessException | TransactionException exception) {
            markTransactionForRollback();
            return new DeleteUrl.DatabaseFailure(exception);
        } catch (RuntimeException exception) {
            markTransactionForRollback();
            return new DeleteUrl.RuntimeFailure(exception);
        }
    }

    @Transactional
    public UrlShortenResult shortenurl(UrlCreateRequest request, UUID userId) {
        try {
            Optional<Users> existingUser = userdbAccess.findById(userId);
            if (existingUser.isEmpty())
            {
                return new UrlShortenResult.NoUserFound();
            }
            Users user = existingUser.get();
    
            String longUrl = request.longUrl();
            OffsetDateTime createdAt = OffsetDateTime.now();
            Urls url = new Urls(longUrl, null, createdAt);
            url.setUser(user);
            if (request.groupId() != null) {
                Optional<Groups> selectedGroup = groupdbaccess.findByGroupIdAndUser_UserId(
                        request.groupId(), userId);
                if (selectedGroup.isEmpty()) {
                    return new UrlShortenResult.NoGroupFound();
                }
                url.setGroup(selectedGroup.get());
            }
            urldbAccess.saveAndFlush(url);
    
            String shortCode = shorten(url.getUrlId());
            url.setShortCode(shortCode);
            urldbAccess.saveAndFlush(url);
    
            return new UrlShortenResult.UrlShortened(new UrlCreateResponse(longUrl, shortCode, createdAt, true));
        
        } catch (DataAccessException | TransactionException exception) {
            markTransactionForRollback();
            return new UrlShortenResult.DatabaseFailure(exception);
        } catch (RuntimeException exception) {
            markTransactionForRollback();
            return new UrlShortenResult.RuntimeFailure(exception);
        }
    } // a type for no id,a type for db failure, a type for shortenurl

    private void markTransactionForRollback() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
    }
}
