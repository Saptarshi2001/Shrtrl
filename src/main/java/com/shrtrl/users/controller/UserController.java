package com.shrtrl.users.controller;

import com.shrtrl.ApiErrorResponse;
import com.shrtrl.users.dto.UserRequest;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.shrtrl.users.service.UserService;

@RestController
public class UserController {

    private UserService userservice;
    @PutMapping("/updateacc")
    public ResponseEntity<?> updateAccount(@RequestBody UserRequest request,@RequestParam int id) {
        try {
            if(!userservice.existsById(id))
            {
              HttpStatus status = HttpStatus.CONFLICT;
              return ResponseEntity.status(status).body(new ApiErrorResponse(status.value(), "Conflict", "user does not exist"));  
            }
            User user=userservice.findById(id);
            user.setuserName(request.username());
            user.setEmail(request.email());
            userservice.save(User);
            return ResponseEntity.status(HttpStatus.OK).body("Account updated");

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

    @DeleteMapping("/deleteacc")
    public ResponseEntity<?> deleteAccount(@RequestParam int id) {
        try {
            if(!userservice.existsById(id))
            {
              HttpStatus status = HttpStatus.CONFLICT;
              return ResponseEntity.status(status).body(new ApiErrorResponse(status.value(), "Conflict", "user does not exist"));  
            }
            userservice.deletebyId(id);
            return ResponseEntity.status(HttpStatus.OK).body("Account Deleted");
        } catch (DataAccessException ex) {
            HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
            return ResponseEntity.status(status).body(new ApiErrorResponse(status.value(), "DataAccessException", ex.getMessage()));

        } catch (RuntimeException ex) {
            HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
            return ResponseEntity.status(status)
                    .body(new ApiErrorResponse(status.value(), "RuntimeException", ex.getMessage()));
        }
    }


@PutMapping("/resetpassword")
    public ResponseEntity<?> updateAccount(@RequestBody String password,@RequestParam int id) {
        try {
            if(!userservice.existsById(id))
            {
              HttpStatus status = HttpStatus.CONFLICT;
              return ResponseEntity.status(status).body(new ApiErrorResponse(status.value(), "Conflict", "user does not exist"));  
            }
            User user=userservice.findById(id);
            user.setPassword(password);
            userservice.save(user);
            return ResponseEntity.status(HttpStatus.OK).body("Password reset done");

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



// caching left
// sessions implementation left
// analytics left
// security left
