
package com.shrtrl.models;
import java.time.OffsetDateTime;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.validator.constraints.Length;
import org.springframework.format.annotation.DateTimeFormat;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.Pattern;

@Entity
@Table(
        name = "urls",
        indexes = {
                @Index(name = "idx_urls_shortcode", columnList = "shortcode"),
                @Index(name = "idx_urls_urlid", columnList = "urlid")
        }
)
public class Urls {

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="urlid",nullable=false,unique=true)
    private int urlId;
    
    @Basic
    @Column(name="longurl",nullable=false)
    @Length(max=20)
    private String longUrl;

    @Basic
    @Column(name="shortcode",unique=true,nullable=true)
    @Length(max=6)
    @Pattern(regexp = "^[a-zA-Z0-9]+$", message = "special characters are not allowed")
    private String shortCode;

    @Column(name="created_date",nullable=false)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime createdDate;

    @Version
    private Long version;
    
    @ManyToOne
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name="userid",nullable=false)
    private Users user;

    @ManyToOne(optional = true)
    @JoinColumn(name = "groupid", nullable = true)
    private Groups group;
    
    protected Urls() {
    }

    public Urls(String longUrl, String shortCode, OffsetDateTime createdDate) {
        this.longUrl =longUrl;
        this.shortCode = shortCode;
        this.createdDate = createdDate;
    }

    

    public int getUrlId() {
        return urlId;
    }

    public void setUrlId(int urlId) {
        this.urlId = urlId;
    }

    public String getlongUrl() {
        return longUrl;
    }

    public void setlongUrl(String originUrl) {
        this.longUrl = originUrl;
    }

    public String getShortCode() {
        return shortCode;
    }

    public void setShortCode(String shortCode) {
        this.shortCode = shortCode;
    }

    public OffsetDateTime getCreatedAt() {
        return createdDate;
    }

    public void setCreatedAt(OffsetDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public Users getUser() {
        return user;
    }

    public void setUser(Users user) {
        this.user = user;
    }

    public Groups getGroup() {
        return group;
    }

    public void setGroup(Groups group) {
        this.group = group;
    }
}
