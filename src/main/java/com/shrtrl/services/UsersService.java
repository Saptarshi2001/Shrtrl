package com.shrtrl.services;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shrtrl.DTOs.UserProfileResponse;
import com.shrtrl.DTOs.UserRequest;
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
    public Optional<UserProfileResponse> showProfile(UUID id) {
        return userdbAccess.findById(id).map(user -> new UserProfileResponse(
                user.getUserName(),
                user.getEmail()
        ));
    }

    @Transactional
    public boolean updateAccount(UUID id, UserRequest request) {
        Optional<Users> containerusr = userdbAccess.findById(id);
        if (containerusr.isEmpty()) {
            return false;
        }
        Users user = containerusr.get();
        Users updateduser=updateuser(user,request);
        return true;
    }

    private Users updateuser(Users user,UserRequest request)
    {
        user.setUserName(request.username());
        user.setEmail(request.email());
        return user;

    }


    @Transactional
    public boolean deleteAccount(UUID id) {
        Optional<Users> containerusr = userdbAccess.findById(id);
        if (containerusr.isEmpty()) {
            return false;
        }

        userdbAccess.delete(containerusr.get());
        return true;
    }
}
