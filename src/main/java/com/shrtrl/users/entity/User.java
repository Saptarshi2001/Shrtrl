package com.shrtrl.users.entity;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class User {
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "userid", unique = true, nullable = false)
    private int userId;

    @Basic
    @Column(name = "username", unique = true, nullable = false)
    private String userName;

    @Basic
    @Column(name = "email", unique = true, nullable = false)
    private String email;

    @Basic
    @Column(name = "password",unique=true, nullable = false)
    private String password;

    
    public User(String userName, String email, String password) {
        this.userName = userName;
        this.email = email;
        this.password = password;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
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

    public String hashPassword(String password)
    {
        String passwordHash = passwordEncoder.encode(password);
        return passwordHash;
    }
}
