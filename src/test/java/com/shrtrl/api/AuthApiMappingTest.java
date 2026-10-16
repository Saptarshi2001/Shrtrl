package com.shrtrl.api;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.shrtrl.ApiExceptionHandler;
import com.shrtrl.controllers.AuthController;
import com.shrtrl.DTOs.CreateUserRequest;
import com.shrtrl.DTOs.LoginRequest;
import com.shrtrl.services.AuthService;
import com.shrtrl.models.Users;
import com.shrtrl.services.UsersService;

import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthApiMappingTest {

    private AuthService authService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        UsersService usersService = mock(UsersService.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authService, usersService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void postRegisterCreatesAccount() throws Exception {
        when(authService.createAccount(new CreateUserRequest(
                "alice",
                "alice@example.com",
                "correct horse battery staple"
        ))).thenReturn(true);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "email": "alice@example.com",
                                  "password": "correct horse battery staple"
                                }
                                """))
                .andExpect(status().isCreated());

        verify(authService).createAccount(new CreateUserRequest(
                "alice",
                "alice@example.com",
                "correct horse battery staple"
        ));
    }

    @Test
    void postRegisterRejectsInvalidInput() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "",
                                  "email": "not-an-email",
                                  "password": "short"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value(400));
    }

    @Test
    void postRegisterReturnsConflictForDuplicateAccount() throws Exception {
        CreateUserRequest request = new CreateUserRequest(
                "alice",
                "alice@example.com",
                "correct horse battery staple"
        );
        when(authService.createAccount(request)).thenReturn(false);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "email": "alice@example.com",
                                  "password": "correct horse battery staple"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.statusCode").value(409));
    }

    @Test
    void postLoginStoresApplicationAndSpringSecuritySessionState() throws Exception {
        UUID userId = UUID.fromString("6e7b5d38-c7ea-43f9-9c80-e71c6bc9a917");
        Users user = new Users("alice", "alice@example.com", "encoded-password");
        user.setUserId(userId);

        LoginRequest requestBody = new LoginRequest(
                "alice@example.com",
                "correct horse battery staple"
        );
        when(authService.authenticate(requestBody)).thenReturn(Optional.of(user));

        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/v1/auth/login")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "alice@example.com",
                                  "password": "correct horse battery staple"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(request().sessionAttribute("USER_ID", userId))
                .andExpect(request().sessionAttribute(
                        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                        notNullValue()
                ));
    }

    @Test
    void postLoginReturnsUnauthorizedForInvalidCredentials() throws Exception {
        LoginRequest requestBody = new LoginRequest(
                "alice@example.com",
                "incorrect password"
        );
        when(authService.authenticate(requestBody)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "alice@example.com",
                                  "password": "incorrect password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.statusCode").value(401));
    }

    @Test
    void postLoginRejectsInvalidBody() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "not-an-email",
                                  "password": ""
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void authMappingsRejectWrongHttpMethods() throws Exception {
        mockMvc.perform(get("/api/v1/auth/register"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(get("/api/v1/auth/login"))
                .andExpect(status().isMethodNotAllowed());
    }
}
