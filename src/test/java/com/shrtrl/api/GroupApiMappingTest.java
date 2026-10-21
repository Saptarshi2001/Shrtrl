package com.shrtrl.api;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.shrtrl.ApiExceptionHandler;
import com.shrtrl.controllers.GroupController;
import com.shrtrl.models.Groups;
import com.shrtrl.services.GroupService;
import com.shrtrl.DTOs.GroupDetailsResponse;
import com.shrtrl.interfaces.groups.CreateGroup;
import com.shrtrl.interfaces.groups.UpdateGroup;
import com.shrtrl.interfaces.groups.DeleteGroup;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GroupApiMappingTest {

    private static final int GROUP_ID = 17;
    private static final UUID USER_ID = UUID.fromString(
            "38a6ce2c-156c-4074-a680-64403f20ad78"
    );

    private GroupService groupService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        groupService = mock(GroupService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new GroupController(groupService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void createUsesSessionOwnerAndValidatedRequestDto() throws Exception {
        when(groupService.createGroup(USER_ID, "work"))
                .thenReturn(new CreateGroup.GroupCreated());
        mockMvc.perform(post("/api/v1/groups")
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupName":"work"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.groupName").value("work"));

        verify(groupService).createGroup(USER_ID, "work");
    }

    @Test
    void createRejectsBlankOrOversizedNames() throws Exception {
        mockMvc.perform(post("/api/v1/groups")
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupName":" "}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/groups")
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupName":"123456789012345678901"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(groupService);
    }

    @Test
    void createRejectsMissingSessionOwner() throws Exception {
        mockMvc.perform(post("/api/v1/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupName":"work"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.statusCode").value(401));

        verifyNoInteractions(groupService);
    }

    @Test
    void createTranslatesDuplicateNameToConflict() throws Exception {
        doThrow(new DataIntegrityViolationException("constraint details"))
                .when(groupService)
                .createGroup(USER_ID, "work");

        mockMvc.perform(post("/api/v1/groups")
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupName":"work"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errmsg")
                        .value("a group with this name already exists"));
    }

    @Test
    void updateIsOwnerScoped() throws Exception {
        when(groupService.updateGroup(GROUP_ID, USER_ID, "renamed"))
                .thenReturn(new UpdateGroup.GroupUpdated());

        mockMvc.perform(put("/api/v1/groups/{groupId}", GROUP_ID)
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupName":"renamed"}
                                """))
                .andExpect(status().isOk());

        verify(groupService).updateGroup(GROUP_ID, USER_ID, "renamed");
    }

    @Test
    void updateTranslatesDuplicateNameToConflict() throws Exception {
        when(groupService.updateGroup(GROUP_ID, USER_ID, "existing"))
                .thenThrow(new DataIntegrityViolationException("constraint details"));

        mockMvc.perform(put("/api/v1/groups/{groupId}", GROUP_ID)
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupName":"existing"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errmsg")
                        .value("a group with this name already exists"));
    }

    @Test
    void deleteIsOwnerScoped() throws Exception {
        when(groupService.deleteGroup(GROUP_ID, USER_ID))
                .thenReturn(new DeleteGroup.GroupDeleted());

        mockMvc.perform(delete("/api/v1/groups/{groupId}", GROUP_ID)
                        .session(authenticatedSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("Group deleted successfully"));

        verify(groupService).deleteGroup(GROUP_ID, USER_ID);
    }

    @Test
    void readIsOwnerScopedAndForeignGroupLooksMissing() throws Exception {
        when(groupService.findGroupDetails(GROUP_ID, USER_ID))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/groups/{groupId}", GROUP_ID)
                        .session(authenticatedSession()))
                .andExpect(status().isNotFound());

        verify(groupService).findGroupDetails(GROUP_ID, USER_ID);
    }

    @Test
    void readReturnsOwnedGroup() throws Exception {
        when(groupService.findGroupDetails(GROUP_ID, USER_ID))
                .thenReturn(Optional.of(new GroupDetailsResponse(GROUP_ID, "work", java.util.List.of())));

        mockMvc.perform(get("/api/v1/groups/{groupId}", GROUP_ID)
                        .session(authenticatedSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupName").value("work"));
    }

    private MockHttpSession authenticatedSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("USER_ID", USER_ID);
        return session;
    }
}
