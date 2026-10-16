# Authentication Module Code Review

## Scope

This review covers the current working-tree authentication implementation and the directly related password, session, OAuth, and authorization interactions:

- `pom.xml`
- `src/main/java/com/shrtrl/config/SecurityConfig.java`
- `src/main/java/com/shrtrl/auth/**`
- `src/main/java/com/shrtrl/users/{controller,service,repository,entity,dto}/**`
- The user/owner inputs consumed by `UrlController` and `GroupController`
- `AuthApiMappingTest`, `UserApiMappingTest`, and the application context test

No development, configuration, or test file was changed.

## Confirmed defects

### AUTH-C1 — Critical — `AuthController` cannot compile

**Location:** `src/main/java/com/shrtrl/auth/controller/AuthController.java:21-26, 40-50, 69-87, 100-126`

**What is wrong:** The class contains multiple independent compile errors:

- `UserService`, `PasswordEncoder`, `HttpSession`, `GetMapping`, `AuthenticationPrincipal`, and `OAuth2User` are used without imports.
- The declared final fields at lines 21-22 are not initialized. The constructor instead assigns an undeclared `userRepository` field, and registration refers to undeclared `userRepository` and `passwordEncoder` names.
- The login parameter `@RequestBody loginrequest` has neither a Java type nor a variable name.
- Login refers to `user` before it is declared, then calls username accessors that do not exist on `LoginRequest`, whose fields are `email` and `password`.
- The Google branch calls a nonexistent `OAuth2User#getEmail()`, closes the `try` block too early, uses `email` outside its scope, omits a semicolon, and has paths that return no `ResponseEntity`.
- All three endpoint methods are named `login`, which is legal overloading once their parameter types are repaired but makes diagnostics and maintenance needlessly confusing.

**Why it matters / reproduction:** Java compilation must succeed before Spring can start. Therefore registration, password login, OAuth login, and logout are all unavailable in this working tree.

**Correction:** Give the controller one constructor that explicitly injects and assigns `UserService`, `UserRepository` (or preferably a single `AuthService`), and `PasswordEncoder`; import the required Spring/Jakarta types; use `LoginRequest request` consistently; look the user up before checking the hash; use `googleUser.getAttribute("email")`; repair braces, semicolons, and return values; and give the endpoint methods distinct Java names. Keep persistence and credential verification in `AuthService` so the controller only validates/marshals HTTP requests.

### AUTH-C2 — Critical — The password service also cannot compile

**Location:** `src/main/java/com/shrtrl/users/service/UserService.java:13-18, 34-45`

**What is wrong:** `resetPassword` calls `passwordEncoder.matches(...)` and `passwordEncoder.encode(...)`, but the class declares and injects only `UserRepository`; no `passwordEncoder` variable exists.

**Why it matters / reproduction:** Even after fixing `AuthController`, main-source compilation still fails here. The intended password-change flow cannot run.

**Correction:** Inject a `PasswordEncoder` through `UserService`'s constructor and store it in a final field, or move password-changing behavior into `AuthService` and inject the encoder there. Verify the current password and encode the new password before saving. Use a specific exception for an invalid current credential rather than `RuntimeException`.

### AUTH-C3 — High — The password-reset controller contradicts the service and would store plaintext

**Location:** `src/main/java/com/shrtrl/users/controller/UserController.java:88-103`; `src/main/java/com/shrtrl/users/service/UserService.java:34-45`

**What is wrong:** The controller does not call the service's `resetPassword` method. It directly assigns `request.newpassword()` to `User.password` and saves the entity. It also checks an undeclared `password` variable at line 91. Thus it currently fails compilation; if that name were mechanically corrected, it would bypass current-password verification and BCrypt encoding.

**Why it matters / reproduction:** A caller able to invoke `PUT /api/v1/users/{id}/password` could select any user ID and replace that user's BCrypt hash with plaintext. Subsequent BCrypt matching would fail for that account, while the raw password would be exposed in the database.

**Correction:** Accept both current and new password in a defined request DTO, derive the target user from the authenticated principal rather than the path, validate both values, and call the encoder-backed service method. Never call `setPassword` with request plaintext in a controller.

### AUTH-C4 — Medium — Internal exception details are returned to clients

**Location:** `src/main/java/com/shrtrl/auth/controller/AuthController.java:58-65, 89-96, 114-121`

**What is wrong:** Database and general runtime exception messages are copied directly into API responses.

**Why it matters / reproduction:** Driver, SQL, schema, or framework errors may reveal implementation details useful to an attacker. It also makes the public error contract depend on unstable internal messages.

**Correction:** Log the full exception server-side with a request/correlation ID and return a fixed generic 500 response. Centralize this behavior in `ApiExceptionHandler`; do not catch broad `RuntimeException` separately in every controller.

## Design and security risks

These risks describe the intended design visible in the source. The compile blockers above currently prevent live exploitation.

### AUTH-R1 — Critical — Session identity is neither enforced nor connected to Spring Security

**Location:** `src/main/java/com/shrtrl/auth/controller/AuthController.java:86-88, 112-113`; `src/main/java/com/shrtrl/users/controller/UserController.java:32-103`; `src/main/java/com/shrtrl/urls/controller/UrlController.java:44-51`; `src/main/java/com/shrtrl/groups/controller/GroupController.java:31-40`

**What is wrong:** Login writes a custom `USER_ID` session attribute, but no filter, interceptor, controller, or service reads it to authenticate or authorize requests. Mutating endpoints instead trust user IDs supplied in a path or query parameter, and user update/delete/password methods do not compare the target ID with the logged-in user.

**Why it matters / reproduction:** If these routes are made reachable, a caller can submit another user's UUID/ID to update or delete that account, reset its password, or create resources attributed to it. Merely setting `USER_ID` does not create a Spring Security `Authentication` or populate the `SecurityContext`.

**Correction:** Use one framework-supported authentication model. For a server-side session application, authenticate through Spring Security, let it persist the `SecurityContext`, and obtain the current user from `Authentication`/`@AuthenticationPrincipal`. Remove owner IDs from client-controlled create operations. For account/resource mutations, load the resource and enforce that its owner matches the authenticated principal (or an authorized admin) before saving/deleting.

### AUTH-R2 — High — There is no application `SecurityFilterChain`

**Location:** `src/main/java/com/shrtrl/config/SecurityConfig.java:8-14`; `pom.xml:114-127`

**What is wrong:** Security dependencies are present, but the only security bean is a password encoder. There are no explicit authorization rules, authentication provider, login/logout integration, OAuth success handling, or API entry-point behavior.

**Why it matters / reproduction:** With Spring Boot security auto-configuration, the fallback policy secures the whole application with generated/default authentication rather than the controller's custom login contract. Registration and custom login are therefore not deliberately permitted, while the custom session attribute still is not recognized as authenticated. If someone later disables the fallback globally to make those endpoints work, all user, URL, and group mutations become unprotected.

**Correction:** Define a `SecurityFilterChain` that explicitly permits only public endpoints (for example registration, the chosen login/OAuth initiation endpoints, and public short-code resolution), requires authentication elsewhere, keeps CSRF protection for cookie/session authentication, and configures framework logout plus the chosen JSON failure/success responses. Add a `UserDetailsService`/`AuthenticationProvider`, or replace the custom password endpoint with Spring Security's supported authentication flow.

### AUTH-R3 — High — Successful login does not rotate the session identifier

**Location:** `src/main/java/com/shrtrl/auth/controller/AuthController.java:69-88, 100-113`

**What is wrong:** Both intended login paths add identity to the pre-existing `HttpSession` without changing its ID.

**Why it matters / reproduction:** If an attacker can make a victim use a known session ID before login, that ID remains valid after `USER_ID` is attached, creating a session-fixation path.

**Correction:** Let Spring Security perform authentication so its session-fixation protection rotates/migrates the session automatically. If a custom flow is retained, call `request.changeSessionId()` only as part of a complete, correctly populated `SecurityContext` authentication flow.

### AUTH-R4 — High — OAuth account linkage relies only on an email

**Location:** `src/main/java/com/shrtrl/auth/controller/AuthController.java:100-113`

**What is wrong:** The intended Google flow locates a local account solely by an `email` claim. It does not verify `email_verified`, retain the provider issuer/subject identity, explicitly link an account, or configure this method as an OAuth success handler. Mapping a controller GET at `/google/success` does not by itself make Spring OAuth2 Login invoke it.

**Why it matters / reproduction:** Email-only implicit linking is fragile and can attach the wrong external identity where claims are absent, unverified, reassigned, or come from a differently configured provider. As written, a valid OAuth callback would normally follow Spring's configured/default success behavior, not reliably execute this method.

**Correction:** Configure `oauth2Login` with an `OAuth2UserService` or authentication-success handler. Require the expected issuer/provider and a verified email, store the stable provider `sub` alongside the local account, and define an explicit account-link/provisioning policy. Do not use an ordinary controller route as a substitute for the OAuth callback/success pipeline.

### AUTH-R5 — Medium — Password login enables account enumeration

**Location:** `src/main/java/com/shrtrl/auth/controller/AuthController.java:72-84`

**What is wrong:** A nonexistent username returns `"user does not exist"`, while a wrong password returns `"Password is incorrect"`.

**Why it matters / reproduction:** Repeated login attempts can identify registered usernames from the response, making targeted password attacks easier. The nonexistent-user path also avoids a password hash check, producing a timing difference.

**Correction:** Return the same generic `401 Unauthorized` response (for example, `"Invalid credentials"`) for both cases. Perform a dummy BCrypt comparison when the user is absent, and add bounded per-account/per-source login throttling.

### AUTH-R6 — Medium — Authentication input has only blank checks

**Location:** `src/main/java/com/shrtrl/auth/dto/CreateUserRequest.java:1-3`; `src/main/java/com/shrtrl/auth/dto/LoginRequest.java:2-4`; `src/main/java/com/shrtrl/auth/controller/AuthController.java:29-38`

**What is wrong:** DTOs have no validation annotations. Registration accepts any nonblank email, arbitrarily weak passwords, and unbounded values. Login performs no null/blank validation at all, and the DTO says `email` while the controller attempts username login.

**Why it matters / reproduction:** Invalid addresses and weak credentials can be persisted; oversized inputs can consume unnecessary BCrypt/DB resources; null credentials can become 500 responses; and clients cannot know whether the supported identifier is email or username.

**Correction:** Choose one login identifier and use it consistently. Add `@NotBlank`, `@Email`, and sensible `@Size` constraints, annotate request bodies with `@Valid`, normalize email/username consistently before uniqueness checks, and enforce a documented password policy and maximum length before BCrypt.

## Test gaps

### AUTH-T1 — High — Controller tests bypass the security layer

**Location:** `src/test/java/com/shrtrl/api/AuthApiMappingTest.java:31-39`; `src/test/java/com/shrtrl/api/UserApiMappingTest.java:31-37`

**Gap:** `MockMvcBuilders.standaloneSetup(...)` does not load the application's Spring Security filter chain. These tests cannot detect that public login/registration is blocked, that protected routes are exposed, that CSRF is mishandled, or that a custom session attribute is not a Spring authentication.

**Correction:** Add `@WebMvcTest` or integration tests with Spring Security enabled. Assert anonymous/public access, `401`/`403` for protected operations, owner-versus-non-owner behavior, CSRF requirements, authenticated principal propagation, and logout behavior.

### AUTH-T2 — High — The login test asserts success without credentials

**Location:** `src/test/java/com/shrtrl/api/AuthApiMappingTest.java:85-89`

**Gap:** The test posts no JSON body, mocks no user lookup or password encoder, and expects `200 OK`. A real login endpoint must reject that request.

**Correction:** Replace it with tests for malformed/missing input, unknown user and wrong password returning the same `401`, a valid BCrypt credential establishing authentication, session-ID rotation, and subsequent authenticated access.

### AUTH-T3 — Medium — Critical authentication branches are untested

**Location:** `src/test/java/com/shrtrl/api/AuthApiMappingTest.java:42-97`; `src/test/java/com/shrtrl/api/UserApiMappingTest.java:104-115`

**Gap:** There are no assertions that registration stores a BCrypt hash rather than plaintext; duplicate checks are race-safe; password change verifies the old password and saves an encoded new one; logout clears framework authentication; OAuth requires verified claims and binds a stable provider subject; or server errors avoid leaking exception messages.

**Correction:** Add focused service tests using a real `BCryptPasswordEncoder`, MVC security tests for session behavior, repository/integration coverage for normalized uniqueness, and OAuth tests with representative verified/unverified/missing claims.

## Verification

- Read the files listed in **Scope** with 1-based line numbering and traced the controller → service → repository/session interactions.
- Attempted `.\mvnw.cmd test`; the wrapper stopped before Maven with `Cannot index into a null array`.
- Attempted the already cached Maven 3.9.16 executable; dependency resolution stopped because `org.springframework.boot:spring-boot-starter-parent:4.0.6` was not locally available and network access was denied.
- Consequently, no test result is claimed. The compile blockers above are direct source-level errors and do not depend on test execution. Existing `target` artifacts were treated as stale and were not used as proof of the current working tree.
