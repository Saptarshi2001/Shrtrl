package com.shrtrl.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shrtrl.DTOs.ApiErrorResponse;
import com.shrtrl.DTOs.ApiMessageResponse;
import com.shrtrl.DTOs.GroupCreateResponse;
import com.shrtrl.DTOs.GroupRequest;
import com.shrtrl.DTOs.GroupDetailsResponse;
import com.shrtrl.DTOs.UpdateGroupResponse;
import com.shrtrl.interfaces.groups.CreateGroup;
import com.shrtrl.interfaces.groups.DeleteGroup;
import com.shrtrl.interfaces.groups.UpdateGroup;
import com.shrtrl.services.GroupService;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/groups")
public class GroupController {

    private static final Logger LOGGER = LoggerFactory.getLogger(GroupController.class);

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }


    @PostMapping
    public ResponseEntity<?> createGroup(
            HttpSession session,
            @Valid @RequestBody GroupRequest request) {
        UUID userId = authenticatedUserId(session);
        if (userId == null) {
            return unauthorized();
        }

        CreateGroup group = groupService.createGroup(userId, request.groupName());
        switch (group) {
            case CreateGroup.GroupCreated created -> {
                return ResponseEntity.status(HttpStatus.CREATED).body(
                        new GroupCreateResponse(request.groupName(), "Group created"));
            }

            case CreateGroup.DuplicateGroup duplicate -> {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(
                        new ApiErrorResponse(409, "Conflict", "a group with this name already exists"));
            }
            case CreateGroup.NoUserFound notfound -> {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        new ApiErrorResponse(404, "Not Found", "No user found"));
            }
            case CreateGroup.DatabaseFailure dbfailure -> {
                LOGGER.error("Could not create group", dbfailure.cause());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                        new ApiErrorResponse(500, "Internal Server Error", "Database operation failed"));
            }
            case CreateGroup.RuntimeFailure runtimefailure -> {
                LOGGER.error("Could not create group", runtimefailure.cause());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                        new ApiErrorResponse(500, "Internal Server Error", "Group could not be created"));
            }
        }
    }



    @PutMapping("/{groupId}")
    public ResponseEntity<?> updateGroupName(
            @PathVariable int groupId,
            HttpSession session,
            @Valid @RequestBody GroupRequest request) {
        UUID userId = authenticatedUserId(session);
        if (userId == null) {
            return unauthorized();
        }

        UpdateGroup group = groupService.updateGroup(groupId, userId, request.groupName());
        switch (group) {
            case UpdateGroup.GroupUpdated updated -> {
                return ResponseEntity.ok(
                        new UpdateGroupResponse(request.groupName(), "Group updated"));
            }
            case UpdateGroup.NoGroupFound notfound -> {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        new ApiErrorResponse(404, "Not Found", "group does not exist"));
            }
            case UpdateGroup.DatabaseFailure dbfailure -> {
                LOGGER.error("Could not update group", dbfailure.cause());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                        new ApiErrorResponse(500, "Internal Server Error", "Database operation failed"));
            }
            case UpdateGroup.RuntimeFailure runtimefailure -> {
                LOGGER.error("Could not update group", runtimefailure.cause());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                        new ApiErrorResponse(500, "Internal Server Error", "Group could not be updated"));
            }
        }
    }

    @DeleteMapping("/{groupId}")
    public ResponseEntity<?> deleteGroup(
            @PathVariable int groupId,
            HttpSession session) {
        UUID userId = authenticatedUserId(session);
        if (userId == null) {
            return unauthorized();
        }

        DeleteGroup group = groupService.deleteGroup(groupId, userId);
        switch (group) {
            case DeleteGroup.GroupDeleted deleted -> {
                return ResponseEntity.ok(new ApiMessageResponse("Group deleted successfully"));
            }
            case DeleteGroup.NoGroupFound notfound -> {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        new ApiErrorResponse(404, "Not Found", "group does not exist"));
            }
            case DeleteGroup.DatabaseFailure dbfailure -> {
                LOGGER.error("Could not delete group", dbfailure.cause());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                        new ApiErrorResponse(500, "Internal Server Error", "Database operation failed"));
            }
            case DeleteGroup.RuntimeFailure runtimefailure -> {
                LOGGER.error("Could not delete group", runtimefailure.cause());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                        new ApiErrorResponse(500, "Internal Server Error", "Group could not be deleted"));
            }
        }
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<?> showGroup(
            @PathVariable int groupId,
            HttpSession session) {
        UUID userId = authenticatedUserId(session);
        if (userId == null) {
            return unauthorized();
        }
        Optional<GroupDetailsResponse> group = groupService.findGroupDetails(groupId, userId);
        if (group.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    new ApiErrorResponse(404, "Not Found", "group does not exist"));
        }
        return ResponseEntity.ok(group.get());
        
    }

    private UUID authenticatedUserId(HttpSession session) {
        Object userId = session.getAttribute("USER_ID");
        return userId instanceof UUID uuid ? uuid : null;
    }

    private ResponseEntity<ApiErrorResponse> unauthorized() {
        HttpStatus status = HttpStatus.UNAUTHORIZED;
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                status.value(),
                "Unauthorized",
                "authenticated user session is missing"
        ));
    }


}
