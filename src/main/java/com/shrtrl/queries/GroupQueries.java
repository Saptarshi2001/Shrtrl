package com.shrtrl.queries;

import com.shrtrl.models.Groups;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GroupQueries extends JpaRepository<Groups, Integer> {
    Optional<Groups> findByGroupIdAndUser_UserId(int groupId, UUID userId);
}
