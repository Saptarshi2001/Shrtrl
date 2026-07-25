package com.shrtrl.urls.controller;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.shrtrl.ApiErrorResponse;

import java.time.LocalDateTime;

import org.springframework.web.bind.annotation.RequestBody;

@RestController
public class UrlController {

    private UrlService urlservice;
    
    @PostMapping("/shortenurl")
    public ResponseEntity<?> shortenUrl(@RequestBody String longurl) {
        try {
            LocalDate datetime=LocalDateTime.now();
            String shortenurl=shorten(longurl);
            Url url=new Url(longurl,shortenurl,LocalDateTime.now())
            urlservice.save(url);
            return ResponseEntity.status(HttpStatus.OK).body(new UrlCreateResponse(longurl,shortenurl,LocalDateTime.now()));
        } catch (DataAccessException ex) {
            HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
            return ResponseEntity.status(status)
                    .body(new ApiErrorResponse(status.value(), "DataAccessException", ex.getMessage()));
        } catch (RuntimeException ex) {
            HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
            return ResponseEntity.status(status)
                    .body(new ApiErrorResponse(status.value(), "RuntimeException", ex.getMessage()));
        }
        
    }

    @GetMapping("/{shortcode}")
    public ResponseEntity<?> shortenUrl(@RequestBody String shortcode) 
    {


    }

    
    @PutMapping("/updatelongurl")
    public ResponseEntity<?> updateLongUrl(@RequestBody String newlongurl,@RequestBody String shortcode) {
        try {

            if(!urlservice.existsByShortCode(shortcode))
            {
            HttpStatus status = HttpStatus.CONFLICT;
              return ResponseEntity.status(status).body(new ApiErrorResponse(status.value(), "Conflict", "url does not exist"));  
            }
            Url url=urlservice.findByShortCode(shortcode);
            url.setOriginUrl(newlongurl);
            urlservice.save(url);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new UpdateUrlResponse(request.longUrl(),request.shortcode(),"Url updated"));

        } catch (DataAccessException ex) {
            HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
            return ResponseEntity.status(status)
                    .body(new ApiErrorResponse(status.value(), "DataAccessException", ex.getMessage()));
        } catch (RuntimeException ex) {
            HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
            return ResponseEntity.status(status)
                    .body(new ApiErrorResponse(status.value(), "RuntimeException", ex.getMessage()));
        }
        
    }

    @DeleteMapping("/deleteurl/{shortcode}")
    public ResponseEntity<?> deleteUrl(@RequestParam String shortcode) {
        try {
            if(!urlservice.existsByShortCode(shortcode))
            {
              HttpStatus status = HttpStatus.CONFLICT;
              return ResponseEntity.status(status).body(new ApiErrorResponse(status.value(), "Conflict", "url does not exist"));  
            }
            return ResponseEntity.status(HttpStatus.OK).body("Url deleted");
        } catch (DataAccessException ex) {
            HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
            return ResponseEntity.status(status)
                    .body(new ApiErrorResponse(status.value(), "DataAccessException", ex.getMessage()));
        } catch (RuntimeException ex) {
            HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
            return ResponseEntity.status(status)
                    .body(new ApiErrorResponse(status.value(), "RuntimeException", ex.getMessage()));
        }
        
    }
    private String shorten(int id)
    {
        
            String chars="0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
            StringBuilder result=new StringBuilder();

            do{
                result.append(chars.charAt((int) (id % 62)));
                id/=62;
            }while(id>0);

            return result.reverse().toString();

        
    }
}
