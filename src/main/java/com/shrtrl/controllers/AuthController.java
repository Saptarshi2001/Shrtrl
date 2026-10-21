package com.shrtrl.controllers;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shrtrl.DTOs.ApiErrorResponse;
import com.shrtrl.DTOs.CreateUserRequest;
import com.shrtrl.DTOs.LoginRequest;
import com.shrtrl.services.AuthService;
import com.shrtrl.interfaces.auth.CreateAccountResult;
import com.shrtrl.interfaces.auth.LoginResult;
import com.shrtrl.models.Users;
import com.shrtrl.services.UsersService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final UsersService usersService;

    public AuthController(AuthService authService, UsersService usersService) {
        this.authService = authService;
        this.usersService = usersService;
    }

    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken csrfToken) {
        return csrfToken;
    }

    @PostMapping("/register")
    public ResponseEntity<?> createAccount(@Valid @RequestBody CreateUserRequest request) {
        CreateAccountResult result;
        
            result = authService.createAccount(request);
         
        switch (result) {
                case CreateAccountResult.AccountCreated created -> {
                    return ResponseEntity.status(HttpStatus.CREATED).body("User created");
                }
                case CreateAccountResult.UserAlreadyExists duplicate -> {
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(
                            new ApiErrorResponse(409, "Conflict", "username or email already exists"));
                }
                case CreateAccountResult.NoUserFound notfound -> {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                            new ApiErrorResponse(404, "Not Found", "No user found"));
                }
                case CreateAccountResult.DatabaseFailure dbfailure -> {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                            new ApiErrorResponse(500, "Internal Server Error", "Database operation failed"));
                }
            }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            HttpServletRequest servletRequest,
            @Valid @RequestBody LoginRequest loginRequest) {
        LoginResult result;
        
            result = authService.authenticate(loginRequest);
        

        switch (result) {
            case LoginResult.InvalidCredentials invalid -> {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                        new ApiErrorResponse(401, "Unauthorized", "email or password is incorrect"));
            }
            case LoginResult.DatabaseFailure failure -> {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                        new ApiErrorResponse(500, "Internal Server Error", "Database operation failed"));
            }
            case LoginResult.RuntimeFailure failure -> {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                        new ApiErrorResponse(500, "Internal Server Error", "Login could not be completed"));
            }
            case LoginResult.AuthenticatedUser authenticated -> {
                Users user = authenticated.user();
                HttpSession session = servletRequest.getSession();
                servletRequest.changeSessionId();
    
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(
                                user.getEmail(),
                                null,
                                List.of()
                        )
                );
    
                SecurityContextHolder.setContext(context);
                session.setAttribute(
                        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                        context
                );
                session.setAttribute("USER_ID", user.getUserId());
    
                return ResponseEntity.ok("Logged in");
            }
        }
    }

    @GetMapping("/google/success")
    public ResponseEntity<?> googleLoginSuccess(
            @AuthenticationPrincipal OAuth2User googleUser,
            HttpSession session) {
        {
            if (googleUser == null) {
                HttpStatus status = HttpStatus.UNAUTHORIZED;
                return ResponseEntity.status(status)
                        .body(new ApiErrorResponse(
                                status.value(),
                                "Unauthorized",
                                "Google authentication was not completed"
                        ));
            }

            String email = googleUser.getAttribute("email");
            if (email == null || email.isBlank()) {
                HttpStatus status = HttpStatus.BAD_REQUEST;
                return ResponseEntity.status(status)
                        .body(new ApiErrorResponse(
                                status.value(),
                                "Bad Request",
                                "Google account did not provide an email"
                        ));
            }

            Optional<Users> user = usersService.findByEmail(email);
            if (user.isEmpty()) {
                HttpStatus status = HttpStatus.UNAUTHORIZED;
                return ResponseEntity.status(status)
                        .body(new ApiErrorResponse(
                                status.value(),
                                "Unauthorized",
                                "user does not exist"
                        ));
            }

            session.setAttribute("USER_ID", user.get().getUserId());
            return ResponseEntity.ok("Logged in");
        } 
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        SecurityContextHolder.clearContext();
        session.invalidate();
        return ResponseEntity.ok("Logged out");
    }

}
