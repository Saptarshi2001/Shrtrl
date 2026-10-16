package com.shrtrl.services;

import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shrtrl.DTOs.CreateUserRequest;
import com.shrtrl.DTOs.LoginRequest;
import com.shrtrl.models.Users;
import com.shrtrl.queries.UserQueries;

@Service
public class AuthService {
    private final UserQueries userRepository;
    private final PasswordEncoder passwordencoder;
    public AuthService(UserQueries userRepository,PasswordEncoder passwordencoder) {
        this.userRepository = userRepository;
        this.passwordencoder = passwordencoder;
    }

    @Transactional
    public boolean createAccount(CreateUserRequest request)
    {
        if (userRepository.existsByUserName(request.username())
                    || userRepository.existsByEmail(request.email())) {
                return false;
            }
            String password=request.password();
            String encrpassword=passwordencoder.encode(password);
            Users user = new Users(request.username(), request.email(), encrpassword);
            userRepository.save(user);
            return true;
    }// a type for no user found, a type for database failure, a type for authorized user

    @Transactional(readOnly = true)
    public Optional<Users> authenticate(LoginRequest request) {
        return userRepository.findByEmail(request.email())
                .filter(user -> passwordencoder.matches(
                        request.password(),
                        user.getPassword()
                ));
    }
// a type for authenticated users, a type for no user found, a type for database failure
    
}
