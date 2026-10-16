package com.shrtrl.models;

import java.util.UUID;

import org.hibernate.validator.constraints.Length;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Email;

@Entity
@Table(name="users",
indexes = {@Index(name = "idx_urls_userid", columnList = "userid")
}
)
public class Users {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "userid", unique = true, nullable = false)
    private UUID userId;

    @Basic
    @Column(name = "username", unique = true, nullable = false,length=30)
    @Length(max=20)
    @Length(min=5)
    @Pattern(regexp = "^[a-zA-Z]+$", message = "Numbers and special characters are not allowed")
    private String userName;

    @Basic
    @Column(name = "email", unique = true, nullable = false)
    @Length(max=30)
    @Length(min=15)
    @Email(message = "email must be valid")
    private String email;

    @Basic
    @Column(name="password",nullable = false,unique=true)
    @Length(min=8)
    private String password;

    
    public Users(String userName, String email, String password) {
        this.userName = userName;
        this.email = email;
        this.password = password;
    }
    protected Users()
    {

    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

}
