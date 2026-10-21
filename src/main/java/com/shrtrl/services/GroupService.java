package com.shrtrl.services;

import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.shrtrl.interfaces.groups.CreateGroup;
import com.shrtrl.interfaces.groups.DeleteGroup;
import com.shrtrl.interfaces.groups.UpdateGroup;
import com.shrtrl.models.Groups;
import com.shrtrl.models.Users;
import com.shrtrl.queries.GroupQueries;
import com.shrtrl.queries.UserQueries;
import com.shrtrl.queries.UrlQueries;
import com.shrtrl.DTOs.GroupDetailsResponse;
import com.shrtrl.DTOs.GroupUrlResponse;

@Service
public class GroupService {

    private final GroupQueries groupRepository;
    private final UserQueries usersRepository;
    private final UrlQueries urlsRepository;

    public GroupService(
            GroupQueries groupRepository,
            UserQueries usersRepository,
            UrlQueries urlsRepository) {
        this.groupRepository = groupRepository;
        this.usersRepository = usersRepository;
        this.urlsRepository = urlsRepository;
    }

    @Transactional
    public CreateGroup createGroup(UUID userId, String groupName) {
        try {
            Optional<Users> existingUser = usersRepository.findById(userId);
            if (existingUser.isEmpty()) {
                return new CreateGroup.NoUserFound();
            }
            if (groupRepository.existsByUser_UserIdAndGroupNameIgnoreCase(userId, groupName)) {
                return new CreateGroup.DuplicateGroup();
            }
            Users user = existingUser.get();
            groupRepository.saveAndFlush(new Groups(groupName, user));
            return new CreateGroup.GroupCreated();
        
        } catch (DataAccessException | TransactionException exception) {
            markTransactionForRollback();
            return new CreateGroup.DatabaseFailure(exception);
        } catch (RuntimeException exception) {
            markTransactionForRollback();
            return new CreateGroup.RuntimeFailure(exception);
        }
    }

    @Transactional
    public UpdateGroup updateGroup(
            int groupId,
            UUID userId,
            String groupName) {
        try {
            Optional<Groups> existingGroup = findGroup(groupId, userId);
            if (existingGroup.isEmpty()) {
                return new UpdateGroup.NoGroupFound();
            }
    
            existingGroup.get().setGroupName(groupName);
            groupRepository.flush();
            return new UpdateGroup.GroupUpdated();
        
        } catch (DataAccessException | TransactionException exception) {
            markTransactionForRollback();
            return new UpdateGroup.DatabaseFailure(exception);
        } catch (RuntimeException exception) {
            markTransactionForRollback();
            return new UpdateGroup.RuntimeFailure(exception);
        }
    }

    @Transactional
    public DeleteGroup deleteGroup(int groupId, UUID userId) {
        try {
            Optional<Groups> existingGroup = findGroup(groupId, userId);
            if (existingGroup.isEmpty()) {
                return new DeleteGroup.NoGroupFound();
            }
    
            groupRepository.delete(existingGroup.get());
            groupRepository.flush();
            return new DeleteGroup.GroupDeleted();
        
        } catch (DataAccessException | TransactionException exception) {
            markTransactionForRollback();
            return new DeleteGroup.DatabaseFailure(exception);
        } catch (RuntimeException exception) {
            markTransactionForRollback();
            return new DeleteGroup.RuntimeFailure(exception);
        }
    }


    @Transactional(readOnly = true)
    public Optional<Groups> findGroup(int groupId, UUID userId) {
        return groupRepository.findByGroupIdAndUser_UserId(groupId, userId);
    }

    @Transactional(readOnly = true)
    public Optional<GroupDetailsResponse> findGroupDetails(int groupId, UUID userId) {
        return findGroup(groupId, userId).map(group -> new GroupDetailsResponse(
                group.getGroupId(),
                group.getGroupName(),
                urlsRepository.findByGroup_GroupIdAndUser_UserIdOrderByUrlIdAsc(groupId, userId)
                        .stream()
                        .map(url -> new GroupUrlResponse(url.getlongUrl(), url.getShortCode()))
                        .toList()));
    }

    private void markTransactionForRollback() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
    }
}
