package com.shrtrl.services;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shrtrl.models.Groups;
import com.shrtrl.models.Users;
import com.shrtrl.queries.GroupQueries;
import com.shrtrl.queries.UserQueries;

@Service
public class GroupService {

    private final GroupQueries groupRepository;
    private final UserQueries usersRepository;

    public GroupService(
            GroupQueries groupRepository,
            UserQueries usersRepository) {
        this.groupRepository = groupRepository;
        this.usersRepository = usersRepository;
    }

    @Transactional
    public void createGroup(UUID userId, String groupName) {
        Users user = usersRepository.findById(userId).orElseThrow(
                () -> new IllegalStateException("Authenticated user not found")
        );
        groupRepository.save(new Groups(groupName, user));
    } // a type for not finding group, a type for database failure, a type for runtime exception, a type for groupcreation

    @Transactional
    public boolean updateGroup(
            int groupId,
            UUID userId,
            String groupName) {
        Optional<Groups> existingGroup = findGroup(groupId, userId);
        if (existingGroup.isEmpty()) {
            return false;
        }

        existingGroup.get().setGroupName(groupName);
        groupRepository.flush();
        return true;
    } // a type for not finding group, a type for database failure, a type for runtime exception, a type for groupupdate

    @Transactional
    public boolean deleteGroup(int groupId, UUID userId) {
        Optional<Groups> existingGroup = findGroup(groupId, userId);
        if (existingGroup.isEmpty()) {
            return false;
        }

        groupRepository.delete(existingGroup.get());
        return true;
    } // a type for not finding group, a type for database failure, a type for runtime exception, a type for groupdelete


    @Transactional(readOnly = true)
    private Optional<Groups> findGroup(int groupId, UUID userId) {
        return groupRepository.findByGroupIdAndUser_UserId(groupId, userId);
    }
}
