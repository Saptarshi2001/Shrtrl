package com.shrtrl.api;

import com.shrtrl.ApiExceptionHandler;
import com.shrtrl.DTOs.UserRequest;
import com.shrtrl.controllers.UserController;
import com.shrtrl.interfaces.users.UpdateAccount;
import com.shrtrl.services.UsersService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiExceptionHandlerTest {
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UserRequest REQUEST = new UserRequest("alice", "alice@example.com");
    private UsersService service;
    private MockMvc mvc;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        service = mock(UsersService.class);
        mvc = MockMvcBuilders.standaloneSetup(new UserController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        session = new MockHttpSession();
        session.setAttribute("USER_ID", USER_ID);
    }

    @Test
    void escapedRuntimeFailureUsesSharedHandler() throws Exception {
        when(service.showProfile(USER_ID)).thenThrow(new IllegalStateException("private details"));
        mvc.perform(get("/api/v1/account").session(session))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errmsg").value("operation could not be completed"));
    }

    @Test
    void escapedIntegrityFailureUsesSharedRuntimeHandler() throws Exception {
        when(service.updateAccount(USER_ID, REQUEST))
                .thenThrow(new DataIntegrityViolationException("private constraint details"));
        update().andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errmsg").value("operation could not be completed"));
    }

    @Test
    void returnedDatabaseFailureStillUsesResultMapping() throws Exception {
        when(service.updateAccount(USER_ID, REQUEST)).thenReturn(
                new UpdateAccount.DatabaseFailure(new DataIntegrityViolationException("duplicate")));
        update().andExpect(status().isConflict())
                .andExpect(jsonPath("$.errmsg").value("username or email already exists"));
    }

    @Test
    void malformedJsonRemainsBadRequest() throws Exception {
        mvc.perform(put("/api/v1/account").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unsupportedMethodRemainsMethodNotAllowed() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/account").session(session))
                .andExpect(status().isMethodNotAllowed());
    }

    private org.springframework.test.web.servlet.ResultActions update() throws Exception {
        return mvc.perform(put("/api/v1/account").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"alice\",\"email\":\"alice@example.com\"}"));
    }
}
