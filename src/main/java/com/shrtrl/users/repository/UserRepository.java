package com.shrtrl.users.repository;

import com.shrtrl.users.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<Url> findByUserName(String userName);
    
    boolean existsByUserName(String userName);
    Optional<Url> findByEmail(String email);

    boolean existsByEmail(String email);
}
