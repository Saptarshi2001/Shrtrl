package com.shrtrl.queries;

import com.shrtrl.models.Urls;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UrlQueries extends JpaRepository<Urls, Integer>
{
    Optional<Urls> findByShortCode(String shortCode);
    Optional<Urls> findByShortCodeAndUser_UserId(String shortCode, UUID userId);
    boolean existsByShortCode(String shortCode);
    void deleteByShortCode(String shortCode);
    Urls save(Urls url);
}
