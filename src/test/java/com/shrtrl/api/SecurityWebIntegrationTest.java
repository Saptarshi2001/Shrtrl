package com.shrtrl.api;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.http.Cookie;

import com.shrtrl.controllers.AuthController;
import com.shrtrl.DTOs.LoginRequest;
import com.shrtrl.services.AuthService;
import com.shrtrl.config.SecurityConfig;
import com.shrtrl.controllers.GroupController;
import com.shrtrl.services.GroupService;
import com.shrtrl.controllers.UrlController;
import com.shrtrl.DTOs.UrlCreateRequest;
import com.shrtrl.DTOs.UrlCreateResponse;
import com.shrtrl.services.UrlCacheService;
import com.shrtrl.services.UrlService;
import com.shrtrl.models.Users;
import com.shrtrl.controllers.UserController;
import com.shrtrl.services.UsersService;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = {
                AuthController.class,
                UrlController.class,
                UserController.class,
                GroupController.class
        },
        excludeAutoConfiguration = ServletWebSecurityAutoConfiguration.class
)
@Import({SecurityConfig.class, SecurityWebIntegrationTest.WebSecurityInfrastructure.class})
class SecurityWebIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class WebSecurityInfrastructure {
    }

    private static final UUID USER_ID = UUID.fromString(
            "6e7b5d38-c7ea-43f9-9c80-e71c6bc9a917"
    );
    private static final Pattern CSRF_TOKEN_PATTERN = Pattern.compile(
            "\\\"token\\\":\\\"([^\\\"]+)\\\""
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Map<String, SecurityFilterChain> securityFilterChains;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UsersService usersService;

    @MockitoBean
    private UrlService urlService;

    @MockitoBean
    private UrlCacheService urlCacheService;

    @MockitoBean
    private com.shrtrl.services.GeoLocationService geoLocationService;

    @MockitoBean
    private com.shrtrl.transport.EventStorage eventStorage;

    @MockitoBean
    private GroupService groupService;

    @Test
    void usesOnlyTheApplicationSecurityFilterChain() {
        assertThat(securityFilterChains).containsOnlyKeys("securityFilterChain");
    }

    @Test
    void csrfEndpointReturnsToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.parameterName").value("_csrf"));
    }

    @Test
    void loginWithoutCsrfIsRejectedBeforeControllerRuns() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "alice@example.com",
                                  "password": "correct horse battery staple"
                                }
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(authService);
    }

    @Test
    void loginWithCsrfCreatesAuthenticatedSession() throws Exception {
        CsrfCredentials csrf = fetchCsrfCredentials();
        Users user = new Users("alice", "alice@example.com", "encoded-password");
        user.setUserId(USER_ID);
        LoginRequest requestBody = new LoginRequest(
                "alice@example.com",
                "correct horse battery staple"
        );
        when(authService.authenticate(requestBody)).thenReturn(new com.shrtrl.interfaces.auth.LoginResult.AuthenticatedUser(user));

        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "alice@example.com",
                                  "password": "correct horse battery staple"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(request().sessionAttribute("USER_ID", USER_ID));
    }

    @Test
    void protectedUrlCreationRejectsUnauthenticatedRequest() throws Exception {
        CsrfCredentials csrf = fetchCsrfCredentials();

        mockMvc.perform(post("/api/v1/urls")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"longUrl":"https://example.com/long"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void protectedUrlCreationRejectsMissingCsrfToken() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .with(user("alice@example.com"))
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"longUrl":"https://example.com/long"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedRequestWithCsrfCanCreateUrl() throws Exception {
        CsrfCredentials csrf = fetchCsrfCredentials();
        UrlCreateRequest request = new UrlCreateRequest("https://example.com/long");
        UrlCreateResponse response = new UrlCreateResponse(
                request.longUrl(),
                "abc123",
                OffsetDateTime.parse("2026-07-26T10:15:30Z"),
                true
        );
        when(urlService.shortenurl(request, USER_ID)).thenReturn(new com.shrtrl.interfaces.urls.UrlShortenResult.UrlShortened(response));

        mockMvc.perform(post("/api/v1/urls")
                        .with(user("alice@example.com"))
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"longUrl":"https://example.com/long"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void publicRedirectDoesNotRequireAuthenticationOrCsrf() throws Exception {
        when(urlCacheService.get("abc123"))
                .thenReturn("https://example.com/destination");

        mockMvc.perform(get("/api/v1/urls/{shortcode}", "abc123"))
                .andExpect(status().isFound())
                .andExpect(header().string(
                        "Location",
                        "https://example.com/destination"
                ));
    }

    @Test
    void protectedUpdateRequiresCsrfEvenWhenAuthenticated() throws Exception {
        mockMvc.perform(put("/api/v1/urls/{shortcode}", "abc123")
                        .with(user("alice@example.com"))
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"longUrl":"https://example.com/new"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedUserCannotDeletePathSuppliedUserId() throws Exception {
        CsrfCredentials csrf = fetchCsrfCredentials();
        UUID otherUserId = UUID.fromString(
                "b5608649-3c14-4f95-b10c-201f368f028b"
        );

        mockMvc.perform(delete("/api/v1/users/{id}", otherUserId)
                        .with(user("alice@example.com"))
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .session(authenticatedSession()))
                .andExpect(status().isNotFound());

        verifyNoInteractions(usersService);
    }

    @Test
    void groupReadRejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/v1/groups/{groupId}", 17))
                .andExpect(status().isForbidden());

        verifyNoInteractions(groupService);
    }

    @Test
    void authenticatedGroupMutationsRejectMissingCsrfToken() throws Exception {
        MockHttpSession session = authenticatedSession();

        mockMvc.perform(post("/api/v1/groups")
                        .with(user("alice@example.com"))
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupName":"work"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/groups/{groupId}", 17)
                        .with(user("alice@example.com"))
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupName":"renamed"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/groups/{groupId}", 17)
                        .with(user("alice@example.com"))
                        .session(session))
                .andExpect(status().isForbidden());

        verifyNoInteractions(groupService);
    }

    @Test
    void authenticatedGroupCreateWithRealCsrfTokenSucceeds() throws Exception {
        when(groupService.createGroup(USER_ID, "work"))
                .thenReturn(new com.shrtrl.interfaces.groups.CreateGroup.GroupCreated());
        CsrfCredentials csrf = fetchCsrfCredentials();

        mockMvc.perform(post("/api/v1/groups")
                        .with(user("alice@example.com"))
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupName":"work"}
                                """))
                .andExpect(status().isCreated());

        verify(groupService).createGroup(USER_ID, "work");
    }

    @Test
    void authenticatedGroupReadRejectsMissingSessionIdentity() throws Exception {
        mockMvc.perform(get("/api/v1/groups/{groupId}", 17)
                        .with(user("alice@example.com")))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(groupService);
    }

    private MockHttpSession authenticatedSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("USER_ID", USER_ID);
        return session;
    }

    private CsrfCredentials fetchCsrfCredentials() throws Exception {
        var result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andReturn();
        Matcher matcher = CSRF_TOKEN_PATTERN.matcher(
                result.getResponse().getContentAsString()
        );
        assertThat(matcher.find()).isTrue();

        return new CsrfCredentials(
                result.getResponse().getCookie("XSRF-TOKEN"),
                matcher.group(1)
        );
    }

    private record CsrfCredentials(Cookie cookie, String token) {
    }
}
