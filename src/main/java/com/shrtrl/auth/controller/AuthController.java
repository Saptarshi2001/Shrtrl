package com.shrtrl.auth.controller;

import com.shrtrl.ApiErrorResponse;
import com.shrtrl.users.dto.CreateUserRequest;
import com.shrtrl.users.entity.User;
import com.shrtrl.users.repository.UserRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

   

    @PostMapping("/createacc")
    public ResponseEntity<?> createAccount(@RequestBody CreateUserRequest request) {
        try {
            User user = new User(request.getUsername(), request.getEmail(), request.getPassword());
            userRepository.save(user);
            return ResponseEntity.status(HttpStatus.OK).body("User Created");
        } catch (DataAccessException ex) {
            HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
            return ResponseEntity.status(status)
                    .body(new ApiErrorResponse(status.value(), "DataAccessException", ex.getMessage()));
        } catch (RuntimeException ex) {
            HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
            return ResponseEntity.status(status)
                    .body(new ApiErrorResponse(status.value(), "RuntimeException", ex.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login() {
        try {

            return ResponseEntity.status(HttpStatus.OK).body("Logged in");
        } catch (DataAccessException ex) {
            HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
            return ResponseEntity.status(status)
                    .body(new ApiErrorResponse(status.value(), "DataAccessException", ex.getMessage()));
        } catch (RuntimeException ex) {
            HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
            return ResponseEntity.status(status)
                    .body(new ApiErrorResponse(status.value(), "RuntimeException", ex.getMessage()));
        }
    }
}
