
package com.shrtrl.urls.entity;
import java.util.Date;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Url {

    @Id
    @GeneratedValue(strategy=GenerationType.UUID)
    @Column(name="urlid",unique=true,nullable=false)
    private int urlId;
    
    @Basic
    @Column(name="originalurl",nullable=false)
    private String originUrl;

    @Basic
    @Column(name="shortcode",unique=true,nullable=false)
    private String shortCode;

    @Date
    @Column(name="createdat",nullable=false)
    private String createdAt;

    @Version
    private Long version;
    
    @ManyToOne
    @OnDelete(Action=OnDelete.Cascade)
    @JoinColumn(name="userid")
    private User user;
    
    public Url(int urlId, String originUrl, String shortCode, Date createdAt) {
        this.urlId = urlId;
        this.originUrl = originUrl;
        this.shortCode = shortCode;
        this.createdAt = createdAt;
    }

    public int getUrlId() {
        return urlId;
    }

    public void setUrlId(int urlId) {
        this.urlId = urlId;
    }

    public String getOriginUrl() {
        return originUrl;
    }

    public void setOriginUrl(String originUrl) {
        this.originUrl = originUrl;
    }

    public String getShortCode() {
        return shortCode;
    }

    public void setShortCode(String shortCode) {
        this.shortCode = shortCode;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}
