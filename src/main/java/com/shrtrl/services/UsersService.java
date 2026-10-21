package com.shrtrl.services;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.shrtrl.DTOs.UserProfileResponse;
import com.shrtrl.DTOs.UserRequest;
import com.shrtrl.interfaces.users.DeleteAccount;
import com.shrtrl.interfaces.users.ShowProfile;
import com.shrtrl.interfaces.users.UpdateAccount;
import com.shrtrl.models.Users;
import com.shrtrl.queries.UserQueries;

@Service
public class UsersService {

    private final UserQueries userdbAccess;
    private final PasswordEncoder encoder;

    public UsersService(UserQueries userdbAccess, PasswordEncoder encoder) {
        this.userdbAccess = userdbAccess;
        this.encoder = encoder;
    }

    public Users save(Users users) {
        return userdbAccess.save(users);
    }

    public List<Users> getUsers() {
        return userdbAccess.findAll();
    }

    public void deletebyId(UUID id) {
        userdbAccess.deleteById(id);
    }

    public boolean existsById(UUID id) {
        return userdbAccess.existsById(id);
    }

    public Optional<Users> findById(UUID id) {
        return userdbAccess.findById(id);
    }

    public Optional<Users> findByUsersName(String usersName) {
        return userdbAccess.findByUserName(usersName);
    }

    public boolean existsByUsersName(String usersName) {
        return userdbAccess.existsByUserName(usersName);
    }

    public boolean existsByEmail(String email) {
        return userdbAccess.existsByEmail(email);
    }

    public Optional<Users> findByEmail(String email) {
        return userdbAccess.findByEmail(email);
    }

    @Transactional
    public boolean changePassword(UUID id, String oldPassWord, String newPassWord) {
        Optional<Users> containerusr = userdbAccess.findById(id);
        Users user = containerusr.orElseThrow(
                () -> new RuntimeException("Users not found")
        );
        
            String updatedPassword = encodepassword(oldPassWord,newPassWord,user.getPassword());
            if (!updatedPassword.equalsIgnoreCase("Password did not matched"))
            {
            user.setPassword(updatedPassword);
            userdbAccess.save(user);
            return true;
        }
        return false;
    }

    public String encodepassword(String oldpassword,String newpassword,String userpassword)
    {
    String updatedpassword="";
if (encoder.matches(oldpassword, userpassword)) {
             updatedpassword = encoder.encode(newpassword);
             return updatedpassword;
}
return "Password did not matched";

    }

    @Transactional(readOnly = true)
    public ShowProfile showProfile(UUID id) {
        try {
            Optional<Users> existingUser = userdbAccess.findById(id);
            if (existingUser.isEmpty()) {
                return new ShowProfile.NoUserFound();
            }
            Users user = existingUser.get();
            return new ShowProfile.ProfileFound(new UserProfileResponse(
                    user.getUserName(), user.getEmail()));
        } catch (DataAccessException | TransactionException exception) {
            markTransactionForRollback();
            return new ShowProfile.DatabaseFailure(exception);
        } catch (RuntimeException exception) {
            markTransactionForRollback();
            return new ShowProfile.RuntimeFailure(exception);
        }
    }

    @Transactional
    public UpdateAccount updateAccount(UUID id, UserRequest request) {
        try {
            Optional<Users> containerusr = userdbAccess.findById(id);
            if (containerusr.isEmpty()) {
                return new UpdateAccount.NoUserFound();
            }
            updateuser(containerusr.get(), request);
            userdbAccess.flush();
            return new UpdateAccount.AccountUpdated();
        } catch (DataAccessException | TransactionException exception) {
            markTransactionForRollback();
            return new UpdateAccount.DatabaseFailure(exception);
        } catch (RuntimeException exception) {
            markTransactionForRollback();
            return new UpdateAccount.RuntimeFailure(exception);
        }
    }

    private Users updateuser(Users user,UserRequest request)
    {
        user.setUserName(request.username());
        user.setEmail(request.email());
        return user;

    }


    @Transactional
    public DeleteAccount deleteAccount(UUID id) {
        try {
            Optional<Users> containerusr = userdbAccess.findById(id);
            if (containerusr.isEmpty()) {
                return new DeleteAccount.NoUserFound();
            }
            userdbAccess.delete(containerusr.get());
            userdbAccess.flush();
            return new DeleteAccount.AccountDeleted();
        } catch (DataAccessException | TransactionException exception) {
            markTransactionForRollback();
            return new DeleteAccount.DatabaseFailure(exception);
        } catch (RuntimeException exception) {
            markTransactionForRollback();
            return new DeleteAccount.RuntimeFailure(exception);
        }
    }

    private void markTransactionForRollback() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
    }
}
