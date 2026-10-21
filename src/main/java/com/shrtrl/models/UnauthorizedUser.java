package com.shrtrl.models;

import org.hibernate.validator.constraints.Length;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Not persisted: there is no guest-account table or repository in the application.
@Table(name="UnauthorizedUser")
public class UnauthorizedUser
{

    @Id
    @GeneratedValue(strategy=GenerationType.SEQUENCE)
    private int sessionid;

    @Column(name="password",nullable=false)
    @Length(max=8)
    private String password;

}
