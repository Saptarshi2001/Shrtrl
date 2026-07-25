package com.shrtrl.groups.repository;

import com.shrtrl.groups.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupRepository extends JpaRepository<Group, Integer> {

    Optional<Group> findByGroupName(String groupName);

    boolean existsByGroupName(String groupName);
    
}
