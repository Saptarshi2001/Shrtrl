package com.shrtrl.controllers;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shrtrl.DTOs.ApiErrorResponse;
import com.shrtrl.DTOs.ApiMessageResponse;
import com.shrtrl.DTOs.ChangePasswordRequest;
import com.shrtrl.DTOs.UserRequest;
import com.shrtrl.interfaces.users.UpdateAccount;
import com.shrtrl.interfaces.users.DeleteAccount;
import com.shrtrl.interfaces.users.ShowProfile;
import com.shrtrl.services.UsersService;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/account")
public class UserController {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserController.class);

    private final UsersService userservice;
    public UserController(UsersService userservice)
    {
        this.userservice=userservice;
    }

    @PutMapping
    public ResponseEntity<?> updateAccount(@Valid @RequestBody UserRequest request, HttpSession session) {
        try {
            if (request == null || request.username() == null || request.username().isBlank()
                    || request.email() == null || request.email().isBlank()) {
                HttpStatus status = HttpStatus.BAD_REQUEST;
                return ResponseEntity.status(status).body(new ApiErrorResponse(status.value(), "Bad Request", "username and email are required"));
            }
            UUID id = authenticatedUserId(session);
            if (id == null) {
                return unauthorized();
            }
            UpdateAccount result = userservice.updateAccount(id, request);
            switch (result) {
                case UpdateAccount.AccountUpdated updated -> {
                    return ResponseEntity.ok(new ApiMessageResponse("Account updated"));
                }
                case UpdateAccount.NoUserFound notFound -> {
                    return ResponseEntity.status(404).body(new ApiErrorResponse(404, "Not Found", "user does not exist"));
                }
                case UpdateAccount.DatabaseFailure failure -> {
                    if (failure.cause() instanceof DataIntegrityViolationException) {
                        return ResponseEntity.status(409).body(new ApiErrorResponse(409, "Conflict", "username or email already exists"));
                    }
                    return internalServerError("updating user account", failure.cause());
                }
                case UpdateAccount.RuntimeFailure failure -> {
                    return internalServerError("updating user account", failure.cause());
                }
            }

        } catch (DataIntegrityViolationException ex) {
            HttpStatus status = HttpStatus.CONFLICT;
            return ResponseEntity.status(status).body(new ApiErrorResponse(status.value(), "Conflict", "username or email already exists"));
        } catch (DataAccessException ex) {
            return internalServerError("updating user account", ex);
        } catch (RuntimeException ex) {
            return internalServerError("updating user account", ex);
        }
    }

    @DeleteMapping
    public ResponseEntity<?> deleteAccount(HttpSession session) {
        try {
            UUID id = authenticatedUserId(session);
            if (id == null) {
                return unauthorized();
            }
            DeleteAccount result = userservice.deleteAccount(id);
            switch (result) {
                case DeleteAccount.AccountDeleted deleted -> {
                    SecurityContextHolder.clearContext();
                    session.invalidate();
                    return ResponseEntity.ok(new ApiMessageResponse("Account deleted"));
                }
                case DeleteAccount.NoUserFound notFound -> {
                    return ResponseEntity.status(404).body(new ApiErrorResponse(404, "Not Found", "user does not exist"));
                }
                case DeleteAccount.DatabaseFailure failure -> {
                    return internalServerError("deleting user account", failure.cause());
                }
                case DeleteAccount.RuntimeFailure failure -> {
                    return internalServerError("deleting user account", failure.cause());
                }
            }

        } catch (DataAccessException ex) {
            return internalServerError("deleting user account", ex);

        } catch (RuntimeException ex) {
            return internalServerError("deleting user account", ex);
        }
    }


    @PutMapping("/password")
    public ResponseEntity<?> resetPassword(@RequestBody ChangePasswordRequest request, HttpSession session) {
        try {
            if (request == null || request.oldPassword() == null || request.newPassword() == null
                    || request.oldPassword().isBlank() || request.newPassword().isBlank()) {
                HttpStatus status = HttpStatus.BAD_REQUEST;
                return ResponseEntity.status(status).body(new ApiErrorResponse(status.value(), "Bad Request", "fill in all the textboxes"));
            }

            UUID id = authenticatedUserId(session);
            if (id == null) {
                return unauthorized();
            }

            if(!userservice.existsById(id))
            {
              HttpStatus status = HttpStatus.NOT_FOUND;
              return ResponseEntity.status(status).body(new ApiErrorResponse(status.value(), "Not Found", "user does not exist"));
            }
            boolean changePasswordresult= userservice.changePassword(id, request.oldPassword(), request.newPassword());
            if(changePasswordresult==true)
            {
              return ResponseEntity.ok(new ApiMessageResponse("Password reset done"));
            }
            HttpStatus status = HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(new ApiErrorResponse(
                    status.value(),
                    "Bad Request",
                    "current password is incorrect"
            ));

        } catch (DataAccessException ex) {
            return internalServerError("changing user password", ex);
        } catch (RuntimeException ex) {
            return internalServerError("changing user password", ex);
        }
    }

    @GetMapping
    public ResponseEntity<?> showProfile(HttpSession session)
    {
        try{
        UUID userid = authenticatedUserId(session);
        if (userid == null) {
            return unauthorized();
        }
        ShowProfile result = userservice.showProfile(userid);
        switch (result) {
            case ShowProfile.ProfileFound found -> {
                return ResponseEntity.ok(found.profile());
            }
            case ShowProfile.NoUserFound notFound -> {
                return ResponseEntity.status(404).body(new ApiErrorResponse(404, "Not Found", "user does not exist"));
            }
            case ShowProfile.DatabaseFailure failure -> {
                return internalServerError("loading user profile", failure.cause());
            }
            case ShowProfile.RuntimeFailure failure -> {
                return internalServerError("loading user profile", failure.cause());
            }
        }

    }catch (DataAccessException ex) {
            return internalServerError("loading user profile", ex);
        } catch (RuntimeException ex) {
            return internalServerError("loading user profile", ex);
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

    private ResponseEntity<ApiErrorResponse> internalServerError(String operation, RuntimeException exception) {
        LOGGER.error("Unexpected error while {}", operation, exception);
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                status.value(),
                "Internal Server Error",
                "user operation could not be completed"
        ));
    }
}


// sessions implementation left
// analytics left
// security left


//M53ECJC24GZ2P7D2
