package com.shrtrl.controllers;

import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
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
import com.shrtrl.DTOs.UpdateGroupResponse;
import com.shrtrl.models.Groups;
import com.shrtrl.services.GroupService;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/groups")
public class GroupController {

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

        try {
            groupService.createGroup(userId, request.groupName());
            return ResponseEntity.status(HttpStatus.CREATED).body(
                    new GroupCreateResponse(
                            request.groupName(),
                            "Group created successfully"
                    )
            );
        } catch (DataIntegrityViolationException exception) {
            return duplicateGroupName();
        } catch (DataAccessException exception) {
            return databaseError();
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

        try {
            if (!groupService.updateGroup(
                    groupId,
                    userId,
                    request.groupName()
            )) {
                return groupNotFound();
            }

            return ResponseEntity.ok(new UpdateGroupResponse(
                    request.groupName(),
                    "Group updated successfully"
            ));
        } catch (DataIntegrityViolationException exception) {
            return duplicateGroupName();
        } catch (DataAccessException exception) {
            return databaseError();
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

        try {
            if (!groupService.deleteGroup(groupId, userId)) {
                return groupNotFound();
            }
            return ResponseEntity.ok(new ApiMessageResponse(
                    "Group deleted successfully"
            ));
        } catch (DataAccessException exception) {
            return databaseError();
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

        try {
            Optional<Groups> group = groupService.findGroup(groupId, userId);
            if (group.isEmpty()) {
                return groupNotFound();
            }

            return ResponseEntity.ok(new UpdateGroupResponse(
                    group.get().getGroupName(),
                    "Group info"
            ));
        } catch (DataAccessException exception) {
            return databaseError();
        }
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

    private ResponseEntity<ApiErrorResponse> groupNotFound() {
        HttpStatus status = HttpStatus.NOT_FOUND;
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                status.value(),
                "Not Found",
                "group does not exist"
        ));
    }

    private ResponseEntity<ApiErrorResponse> duplicateGroupName() {
        HttpStatus status = HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                status.value(),
                "Conflict",
                "a group with this name already exists"
        ));
    }

    private ResponseEntity<ApiErrorResponse> databaseError() {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                status.value(),
                "Internal Server Error",
                "group operation could not be completed"
        ));
    }
}
