package com.shrtrl.interfaces.auth;

import org.springframework.dao.DataIntegrityViolationException;

public sealed interface CreateAccountResult
{
    record NoUserFound() implements CreateAccountResult {}
    record UserAlreadyExists() implements CreateAccountResult {}
    record DatabaseFailure() implements CreateAccountResult {}
    record AccountCreated() implements CreateAccountResult {}
} 
