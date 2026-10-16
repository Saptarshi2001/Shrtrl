package com.shrtrl.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.UUID;

import com.shrtrl.groups.entity.Group;
import com.shrtrl.queries.GroupQueries;
import com.shrtrl.urls.entity.Url;
import com.shrtrl.urls.repository.UrlRepository;
import com.shrtrl.models.User;
import com.shrtrl.users.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@ActiveProfiles("sqlite-integration")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SqliteJpaIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UrlRepository urlRepository;

    @Autowired
    private GroupQueries groupRepository;

    @Autowired
    private EntityManager entityManager;

    @Nested
    class Users {

        @Test
        void persistsGeneratedUuidAndSupportsLookups() {
            User saved = userRepository.saveAndFlush(
                    new User("alice", "alice@example.com", "encoded-password"));

            assertThat(saved.getUserId()).isNotNull();
            assertThat(saved.getUserId()).isInstanceOf(UUID.class);

            entityManager.clear();

            assertThat(userRepository.findById(saved.getUserId()))
                    .isPresent()
                    .get()
                    .extracting(User::getEmail)
                    .isEqualTo("alice@example.com");
            assertThat(userRepository.findByUserName("alice")).isPresent();
            assertThat(userRepository.findByEmail("alice@example.com")).isPresent();
            assertThat(userRepository.existsByUserName("alice")).isTrue();
            assertThat(userRepository.existsByEmail("alice@example.com")).isTrue();
        }

        @Test
        void rejectsDuplicateUsername() {
            userRepository.saveAndFlush(
                    new User("alice", "alice@example.com", "encoded-password-1"));

            assertThatThrownBy(() -> userRepository.saveAndFlush(
                    new User("alice", "other@example.com", "encoded-password-2")))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void rejectsDuplicateEmail() {
            userRepository.saveAndFlush(
                    new User("alice", "shared@example.com", "encoded-password-1"));

            assertThatThrownBy(() -> userRepository.saveAndFlush(
                    new User("bob", "shared@example.com", "encoded-password-2")))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    class Urls {

        @Test
        void persistsGeneratedUuidOwnerAndShortCodeLookup() {
            User owner = savedUser("url-owner");
            Url url = url(owner, "https://example.com/one", "one123");

            Url saved = urlRepository.saveAndFlush(url);
            entityManager.clear();

            assertThat(saved.getUrlId()).isNotNull();
            assertThat(saved.getUrlId()).isInstanceOf(UUID.class);

            Url loaded = urlRepository.findByShortCode("one123").orElseThrow();
            assertThat(loaded.getUrlId()).isEqualTo(saved.getUrlId());
            assertThat(ReflectionTestUtils.getField(loaded, "longUrl"))
                    .isEqualTo("https://example.com/one");
            User loadedOwner = (User) ReflectionTestUtils.getField(loaded, "user");
            assertThat(loadedOwner).isNotNull();
            assertThat(loadedOwner.getUserId()).isEqualTo(owner.getUserId());
            assertThat(urlRepository.existsByShortCode("one123")).isTrue();
        }

        @Test
        void rejectsDuplicateShortCode() {
            User owner = savedUser("duplicate-url-owner");
            urlRepository.saveAndFlush(
                    url(owner, "https://example.com/one", "same-code"));

            assertThatThrownBy(() -> urlRepository.saveAndFlush(
                    url(owner, "https://example.com/two", "same-code")))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void deletesByShortCode() {
            User owner = savedUser("delete-url-owner");
            urlRepository.saveAndFlush(
                    url(owner, "https://example.com/delete", "delete-me"));

            urlRepository.deleteByShortCode("delete-me");
            urlRepository.flush();
            entityManager.clear();

            assertThat(urlRepository.findByShortCode("delete-me")).isEmpty();
        }
    }

    @Nested
    class Groups {

        @Test
        void persistsGeneratedUuidOwnerAndNameLookup() {
            User owner = savedUser("group-owner");
            Group group = group(owner, "team-one");

            Group saved = groupRepository.saveAndFlush(group);
            entityManager.clear();

            assertThat((Object) saved.getGroupId()).isInstanceOf(UUID.class);

            assertThat(groupRepository.findById(saved.getGroupId())).isPresent();
            Group loaded = groupRepository.findByGroupName("team-one").orElseThrow();
            assertThat(loaded.getGroupId()).isEqualTo(saved.getGroupId());
            assertThat(loaded.getUser().getUserId()).isEqualTo(owner.getUserId());
            assertThat(groupRepository.existsByGroupName("team-one")).isTrue();
        }

        @Test
        void rejectsDuplicateGroupName() {
            User owner = savedUser("duplicate-group-owner");
            groupRepository.saveAndFlush(group(owner, "same-name"));

            assertThatThrownBy(() -> groupRepository.saveAndFlush(
                    group(owner, "same-name")))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void deletesGroupUsingInheritedRepositoryOperation() {
            User owner = savedUser("delete-group-owner");
            Group saved = groupRepository.saveAndFlush(group(owner, "obsolete"));

            groupRepository.delete(saved);
            groupRepository.flush();
            entityManager.clear();

            assertThat(groupRepository.findByGroupName("obsolete")).isEmpty();
        }
    }

    private User savedUser(String name) {
        return userRepository.saveAndFlush(
                new User(name, name + "@example.com", "encoded-password"));
    }

    private Url url(User owner, String longUrl, String shortCode) {
        Url url = new Url(longUrl, shortCode, LocalDateTime.now());
        ReflectionTestUtils.setField(url, "user", owner);
        return url;
    }

    private Group group(User owner, String name) {
        Group group = new Group();
        group.setGroupName(name);
        group.setUser(owner);
        return group;
    }
}
