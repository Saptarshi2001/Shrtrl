package com.shrtrl.services;

import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.shrtrl.DTOs.CreateUserRequest;
import com.shrtrl.DTOs.LoginRequest;
import com.shrtrl.models.Users;
import com.shrtrl.queries.UserQueries;
import com.shrtrl.interfaces.auth.CreateAccountResult;
import com.shrtrl.interfaces.auth.LoginResult;

@Service
public class AuthService {
    private final UserQueries userRepository;
    private final PasswordEncoder passwordencoder;
    public AuthService(UserQueries userRepository,PasswordEncoder passwordencoder) {
        this.userRepository = userRepository;
        this.passwordencoder = passwordencoder;
    }

    @Transactional
    public CreateAccountResult createAccount(CreateUserRequest request)
    {
        String encrpassword=encodepassword(request);
        if (userRepository.existsByUserName(request.username()))
        {
        return new CreateAccountResult.UserAlreadyExists();
        }
         else if (userRepository.existsByEmail(request.email())) {
                return new CreateAccountResult.UserAlreadyExists();
            }
            Users user = new Users(request.username(), request.email(), encrpassword);
            userRepository.save(user);
            return new CreateAccountResult.AccountCreated();
    }// a type for no user found, a type for database failure, a type for authorized user

    private String encodepassword(CreateUserRequest request)
    {
      String password=request.password();
      String encrpassword=passwordencoder.encode(password);  
      return encrpassword;
    }
    @Transactional(readOnly = true)
    public LoginResult authenticate(LoginRequest request) {
        try {
            return userRepository.findByEmail(request.email())
                .filter(user -> passwordencoder.matches(
                        request.password(),
                        user.getPassword()
                ))
                .<LoginResult>map(LoginResult.AuthenticatedUser::new)
                .orElseGet(LoginResult.InvalidCredentials::new);
        } catch (DataAccessException | TransactionException exception) {
            markTransactionForRollback();
            return new LoginResult.DatabaseFailure(exception);
        } catch (RuntimeException exception) {
            markTransactionForRollback();
            return new LoginResult.RuntimeFailure(exception);
        }
    }

    private void markTransactionForRollback() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
    }
// a type for authenticated users, a type for no user found, a type for database failure
    
}
