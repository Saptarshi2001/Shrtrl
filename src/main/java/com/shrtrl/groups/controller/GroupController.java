package com.shrtrl.groups.controller;

import com.shrtrl.ApiErrorResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GroupController {

    private GroupService service;
    @PostMapping("/creategroup")
    public ResponseEntity<?> createGroup(@RequestBody String groupName) {
        try {
            if(!groupservice.existsById(groupid))
            {
              HttpStatus status = HttpStatus.CONFLICT;
              return ResponseEntity.status(status).body(new ApiErrorResponse(status.value(), "Conflict", "group does not exist"));  
            }
            return ResponseEntity.status(HttpStatus.OK).body(new GroupCreateResponse(groupName,"Group created successfully"));
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

    @PostMapping("/mergegroup")
    public ResponseEntity<?> mergeGroup() {
        try {
            if(!groupservice.existsById(groupid))
            {
              HttpStatus status = HttpStatus.CONFLICT;
              return ResponseEntity.status(status).body(new ApiErrorResponse(status.value(), "Conflict", "group does not exist"));  
            }
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

    @PutMapping("/updateGroupName")
    public ResponseEntity<?> updateGroupName(@RequestBody String groupName) {
        try {
            if(!groupservice.existsById(groupid))
            {
              HttpStatus status = HttpStatus.CONFLICT;
              return ResponseEntity.status(status).body(new ApiErrorResponse(status.value(), "Conflict", "group does not exist"));  
            }
            return ResponseEntity.status(HttpStatus.OK).body(new UpdateGroupResponse(groupName,"Group updated successfully"));
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

    @DeleteMapping("/deletegroup")
    public ResponseEntity<?> deleteGroup(@RequestParam int groupid,@RequestParam String groupName) {
        try {
            if(!groupservice.existsById(groupid))
            {
              HttpStatus status = HttpStatus.CONFLICT;
              return ResponseEntity.status(status).body(new ApiErrorResponse(status.value(), "Conflict", "group does not exist"));  
            }
            return ResponseEntity.status(HttpStatus.OK).body(new DeleteGroupResponse(groupName,"Group deleted successfully")));
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
