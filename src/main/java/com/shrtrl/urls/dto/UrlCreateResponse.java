
package com.shrtrl.urls.dto;
import java.util.Date;

public record UrlCreateResponse(String LongUrl,String shortCode,Date createdAt){}