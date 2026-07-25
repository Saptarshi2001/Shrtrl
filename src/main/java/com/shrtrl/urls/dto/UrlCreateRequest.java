package com.shrtrl.urls.dto;
import java.util.Date;

public record UrlCreateRequest(String LongUrl,String shortCode,Date createdAt){}