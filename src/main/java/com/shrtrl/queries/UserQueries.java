package com.shrtrl.queries;
import java.util.Optional;
import java.util.UUID;
import com.shrtrl.models.Users;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserQueries extends JpaRepository<Users, UUID> {
    Optional<Users> findByUserName(String userName);
    boolean existsByUserName(String userName);
    Optional<Users> findByEmail(String email);
    boolean existsByEmail(String email);
}
