package com.shrtrl.api;

import java.util.UUID;
import com.shrtrl.interfaces.users.UpdateAccount;
import com.shrtrl.interfaces.users.DeleteAccount;
import com.shrtrl.interfaces.users.ShowProfile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.shrtrl.ApiExceptionHandler;
import com.shrtrl.controllers.UserController;
import com.shrtrl.DTOs.UserProfileResponse;
import com.shrtrl.DTOs.UserRequest;
import com.shrtrl.services.UsersService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserApiMappingTest {

    private static final UUID USER_ID = UUID.fromString(
            "38a6ce2c-156c-4074-a680-64403f20ad78"
    );
    private static final UUID OTHER_USER_ID = UUID.fromString(
            "b5608649-3c14-4f95-b10c-201f368f028b"
    );

    private UsersService usersService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        usersService = mock(UsersService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new UserController(usersService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void updateUsesAuthenticatedSessionUserId() throws Exception {
        UserRequest request = new UserRequest("new-name", "new@example.com");
        when(usersService.updateAccount(USER_ID, request)).thenReturn(new UpdateAccount.AccountUpdated());

        mockMvc.perform(put("/api/v1/account")
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "new-name",
                                  "email": "new@example.com"
                                }
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Account updated"));

        verify(usersService).updateAccount(USER_ID, request);
    }

    @Test
    void updateRejectsMissingSessionUserId() throws Exception {
        mockMvc.perform(put("/api/v1/account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "new-name",
                                  "email": "new@example.com"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.statusCode").value(401));

        verifyNoInteractions(usersService);
    }

    @Test
    void deleteUsesAuthenticatedSessionUserId() throws Exception {
        when(usersService.deleteAccount(USER_ID)).thenReturn(new DeleteAccount.AccountDeleted());

        mockMvc.perform(delete("/api/v1/account")
                        .session(authenticatedSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Account deleted"));

        verify(usersService).deleteAccount(USER_ID);
    }

    @Test
    void pathSuppliedUserIdCannotDeleteAnotherAccount() throws Exception {
        mockMvc.perform(delete("/api/v1/users/{id}", OTHER_USER_ID)
                        .session(authenticatedSession()))
                .andExpect(status().isNotFound());

        verifyNoInteractions(usersService);
    }

    @Test
    void passwordChangeUsesAuthenticatedSessionUserId() throws Exception {
        when(usersService.existsById(USER_ID)).thenReturn(true);
        when(usersService.changePassword(USER_ID, "old-password", "new-password"))
                .thenReturn(true);

        mockMvc.perform(put("/api/v1/account/password")
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "oldPassword": "old-password",
                                  "newPassword": "new-password"
                                }
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password reset done"));

        verify(usersService).changePassword(
                USER_ID,
                "old-password",
                "new-password"
        );
    }

    @Test
    void incorrectCurrentPasswordUsesTypedErrorResponse() throws Exception {
        when(usersService.existsById(USER_ID)).thenReturn(true);
        when(usersService.changePassword(USER_ID, "wrong-password", "new-password"))
                .thenReturn(false);

        mockMvc.perform(put("/api/v1/account/password")
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "oldPassword": "wrong-password",
                                  "newPassword": "new-password"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value(400))
                .andExpect(jsonPath("$.errorType").value("Bad Request"))
                .andExpect(jsonPath("$.errmsg")
                        .value("current password is incorrect"));
    }

    @Test
    void profileUsesAuthenticatedSessionUserId() throws Exception {
        when(usersService.showProfile(USER_ID)).thenReturn(
                new ShowProfile.ProfileFound(new UserProfileResponse(
                        "alice",
                        "alice@example.com"
                ))
        );

        mockMvc.perform(get("/api/v1/account")
                        .session(authenticatedSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userName").value("alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.result").doesNotExist())
                .andExpect(jsonPath("$.message").doesNotExist());

        verify(usersService).showProfile(USER_ID);
    }

    private MockHttpSession authenticatedSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("USER_ID", USER_ID);
        return session;
    }
}

