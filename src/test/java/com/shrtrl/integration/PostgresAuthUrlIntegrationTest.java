package com.shrtrl.integration;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.shrtrl.models.Groups;
import com.shrtrl.queries.GroupQueries;
import com.shrtrl.models.Urls;
import com.shrtrl.queries.UrlQueries;
import com.shrtrl.models.Users;
import com.shrtrl.queries.UserQueries;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostgresAuthUrlIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("shrtrl_test")
            .withUsername("shrtrl")
            .withPassword("shrtrl_test_password");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private UserQueries usersRepository;

    @Autowired
    private UrlQueries urlsRepository;

    @Autowired
    private GroupQueries groupRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void flywaySchemaPersistsUrlOwnerAndSupportsOwnerScopedLookup() {
        Users owner = usersRepository.saveAndFlush(new Users(
                "alice",
                "alice@example.com",
                "encoded-password"
        ));

        Urls url = new Urls(
                "https://example.com/long",
                null,
                OffsetDateTime.parse("2026-07-26T10:15:30Z")
        );
        url.setUser(owner);
        urlsRepository.saveAndFlush(url);

        url.setShortCode("abc123");
        urlsRepository.saveAndFlush(url);
        entityManager.clear();

        assertThat(url.getUrlId()).isPositive();
        assertThat(urlsRepository.findByShortCodeAndUser_UserId(
                "abc123",
                owner.getUserId()
        )).isPresent();
        assertThat(urlsRepository.findByShortCodeAndUser_UserId(
                "abc123",
                UUID.randomUUID()
        )).isEmpty();
    }

    @Test
    void databaseRejectsUrlWithoutOwner() {
        Urls url = new Urls(
                "https://example.com/unowned",
                null,
                OffsetDateTime.parse("2026-07-26T10:15:30Z")
        );

        assertThatThrownBy(() -> urlsRepository.saveAndFlush(url))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void groupLookupIsScopedToItsOwner() {
        Users owner = usersRepository.saveAndFlush(new Users(
                "group-owner",
                "group-owner@example.com",
                "encoded-password"
        ));
        Users otherUser = usersRepository.saveAndFlush(new Users(
                "other-user",
                "other-user@example.com",
                "encoded-password"
        ));
        Groups group = groupRepository.saveAndFlush(new Groups("work", owner));
        entityManager.clear();

        assertThat(groupRepository.findByGroupIdAndUser_UserId(
                group.getGroupId(),
                owner.getUserId()
        )).isPresent();
        assertThat(groupRepository.findByGroupIdAndUser_UserId(
                group.getGroupId(),
                otherUser.getUserId()
        )).isEmpty();
    }

    @Test
    void databaseRejectsDuplicateGroupNameForSameOwner() {
        Users owner = usersRepository.saveAndFlush(new Users(
                "duplicate-owner",
                "duplicate-owner@example.com",
                "encoded-password"
        ));
        groupRepository.saveAndFlush(new Groups("work", owner));

        assertThatThrownBy(() -> groupRepository.saveAndFlush(
                new Groups("work", owner)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingUserCascadesToOwnedGroups() {
        Users owner = usersRepository.saveAndFlush(new Users(
                "cascade-owner",
                "cascade-owner@example.com",
                "encoded-password"
        ));
        Groups group = groupRepository.saveAndFlush(new Groups("work", owner));
        int groupId = group.getGroupId();

        usersRepository.delete(owner);
        usersRepository.flush();
        entityManager.clear();

        assertThat(groupRepository.findById(groupId)).isEmpty();
    }
}
