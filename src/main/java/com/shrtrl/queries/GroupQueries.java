package com.shrtrl.queries;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shrtrl.models.Groups;

public interface GroupQueries extends JpaRepository<Groups, Integer> {
    boolean existsByUser_UserIdAndGroupNameIgnoreCase(UUID userId, String groupName);

    Optional<Groups> findByGroupIdAndUser_UserId(int groupId, UUID userId);
    
}
