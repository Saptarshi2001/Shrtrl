# Spring Boot HTTP Application Classes and Functions

This guide covers common classes, interfaces, and methods used in Servlet-based Spring Boot HTTP applications. It complements `spring-boot-http-annotations.md`.

## Application startup and dependency injection

| Class or interface | Common method | Purpose |
| --- | --- | --- |
| `SpringApplication` | `run(...)` | Starts the Spring Boot application and creates its application context. |
| `ApplicationContext` | `getBean(...)` | Represents Spring's bean container. Direct lookups are uncommon; constructor injection is preferred. |

Example:

```java
public static void main(String[] args) {
    SpringApplication.run(ShrtrlApplication.class, args);
}
```

Use constructor injection for application dependencies:

```java
public UrlController(UrlService urlService) {
    this.urlService = urlService;
}
```

## Controller request values

Spring MVC can supply these types directly as controller method parameters.

| Class or interface | Useful methods | Purpose |
| --- | --- | --- |
| `HttpServletRequest` | `getHeader()`, `getParameter()`, `getCookies()`, `getRemoteAddr()` | Provides low-level access to the incoming HTTP request. Prefer annotated parameters for ordinary values. |
| `HttpServletResponse` | `setStatus()`, `setHeader()`, `addCookie()` | Provides low-level control of the outgoing response. Prefer `ResponseEntity` for most API responses. |
| `HttpSession` | `getAttribute()`, `setAttribute()`, `removeAttribute()`, `invalidate()` | Stores server-side data associated with a browser session. |
| `Principal` | `getName()` | Provides the authenticated user's name through the standard Java security API. |
| `Authentication` | `getName()`, `getPrincipal()`, `getAuthorities()`, `isAuthenticated()` | Provides the current Spring Security authentication and authorities. |
| `Locale` | — | Represents the locale resolved for the current request. |

Example:

```java
@GetMapping("/me")
public UserResponse currentUser(Authentication authentication) {
    return userService.findByLogin(authentication.getName());
}
```

Prefer `@PathVariable`, `@RequestParam`, `@RequestHeader`, and `@RequestBody` over manually reading the request when possible.

## Responses, status codes, and headers

| Class or interface | Useful methods or constants | Purpose |
| --- | --- | --- |
| `ResponseEntity<T>` | `ok()`, `status()`, `created()`, `noContent()`, `notFound()`, `body()` | Builds an HTTP response with a body, status, and headers. |
| `HttpStatus` | `OK`, `CREATED`, `NO_CONTENT`, `BAD_REQUEST`, `NOT_FOUND` | Enum of standard HTTP status codes. |
| `HttpStatusCode` | `value()`, `is2xxSuccessful()`, `is4xxClientError()` | General abstraction for HTTP status codes, including non-enum codes. |
| `HttpHeaders` | `set()`, `add()`, `getFirst()`, `setContentType()`, `setLocation()` | Stores HTTP headers and provides constants such as `AUTHORIZATION`. |
| `MediaType` | `APPLICATION_JSON`, `TEXT_PLAIN`, `MULTIPART_FORM_DATA` | Represents request and response content types. |
| `CacheControl` | `maxAge()`, `noCache()`, `noStore()` | Builds the value of the `Cache-Control` response header. |

Examples:

```java
return ResponseEntity.ok(profile);
```

```java
URI location = URI.create("/api/urls/" + created.id());

return ResponseEntity.created(location)
        .body(created);
```

```java
return ResponseEntity.noContent().build();
```

## Redirects and URI building

| Class | Useful methods | Purpose |
| --- | --- | --- |
| `URI` | `create()` | Represents a URI used in redirects, locations, and client requests. |
| `UriComponentsBuilder` | `fromPath()`, `pathSegment()`, `queryParam()`, `build()`, `toUri()` | Safely constructs and encodes URIs. |
| `ServletUriComponentsBuilder` | `fromCurrentRequest()`, `fromCurrentContextPath()` | Builds a URI using the current Servlet request. |

Example:

```java
URI location = ServletUriComponentsBuilder
        .fromCurrentRequest()
        .pathSegment("{id}")
        .buildAndExpand(savedUrl.id())
        .toUri();

return ResponseEntity.created(location).body(savedUrl);
```

For a redirect response:

```java
return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(originalUrl))
        .build();
```

## JSON serialization and request DTOs

| Class or interface | Useful methods | Purpose |
| --- | --- | --- |
| `ObjectMapper` | `readValue()`, `writeValueAsString()`, `convertValue()` | Jackson's main JSON serialization and deserialization class. Spring MVC normally calls it automatically. |
| Java `record` | Generated constructor and accessors | A concise type for immutable request and response DTOs. |
| `JsonNode` | `get()`, `path()`, `asText()` | Represents dynamic JSON when its structure is not fixed. Prefer typed DTOs for normal APIs. |

Example DTOs:

```java
public record CreateUrlRequest(String originalUrl) {}

public record UrlResponse(Long id, String shortCode, String originalUrl) {}
```

Controller methods can use them directly:

```java
@PostMapping
public ResponseEntity<UrlResponse> create(
        @Valid @RequestBody CreateUrlRequest request
) {
    UrlResponse created = urlService.create(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
}
```

Avoid returning JPA entities directly. Response DTOs prevent accidental exposure of internal fields and persistence relationships.

## Validation and binding results

| Class or interface | Useful methods | Purpose |
| --- | --- | --- |
| `BindingResult` | `hasErrors()`, `getFieldErrors()`, `getAllErrors()` | Holds validation and data-binding errors when declared immediately after the validated parameter. |
| `FieldError` | `getField()`, `getDefaultMessage()`, `getRejectedValue()` | Describes a validation error for one field. |
| `Validator` | `supports()`, `validate()` | Interface for custom Spring validation logic. Bean Validation annotations are simpler for most DTOs. |

Example:

```java
@PostMapping
public ResponseEntity<?> create(
        @Valid @RequestBody CreateUserRequest request,
        BindingResult result
) {
    if (result.hasErrors()) {
        List<String> errors = result.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .toList();

        return ResponseEntity.badRequest().body(errors);
    }

    return ResponseEntity.status(HttpStatus.CREATED)
            .body(userService.create(request));
}
```

For a REST API, a centralized exception handler is usually cleaner than repeating `BindingResult` handling in every controller.

## Errors and API error responses

| Class or interface | Useful methods | Purpose |
| --- | --- | --- |
| `ResponseStatusException` | Constructor with status and reason | Stops request processing with a chosen HTTP status. Useful for small applications. |
| `ProblemDetail` | `forStatus()`, `forStatusAndDetail()`, `setTitle()`, `setProperty()` | Represents an RFC 9457 problem-details response. |
| `ErrorResponse` | `getStatusCode()`, `getBody()` | Spring interface implemented by web exceptions that expose a structured error response. |

Example:

```java
User user = userRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "User not found"
        ));
```

Centralized problem response:

```java
@ExceptionHandler(ResourceNotFoundException.class)
public ResponseEntity<ProblemDetail> handleNotFound(
        ResourceNotFoundException exception
) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            exception.getMessage()
    );
    problem.setTitle("Resource not found");

    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
}
```

## Sessions and cookies

| Class | Useful methods | Purpose |
| --- | --- | --- |
| `HttpSession` | `getId()`, `getAttribute()`, `setAttribute()`, `invalidate()` | Accesses server-side session state. The browser normally sends the session cookie automatically. |
| `Cookie` | `getName()`, `getValue()`, `setMaxAge()`, `setHttpOnly()`, `setSecure()` | Represents a Servlet cookie. |
| `ResponseCookie` | `from()`, `httpOnly()`, `secure()`, `sameSite()`, `maxAge()`, `build()` | Builds a response cookie with modern security attributes. |

Example:

```java
Long userId = (Long) session.getAttribute("USER_ID");
```

```java
ResponseCookie cookie = ResponseCookie.from("theme", "dark")
        .httpOnly(true)
        .secure(true)
        .sameSite("Lax")
        .path("/")
        .maxAge(Duration.ofDays(30))
        .build();

return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookie.toString())
        .build();
```

Do not use a session ID itself as a database user ID. Spring resolves the session first; your application then reads the authenticated principal or a session attribute.

## Spring Security principals

| Class or interface | Useful methods | Purpose |
| --- | --- | --- |
| `SecurityFilterChain` | Configured through `HttpSecurity` | Defines authentication, authorization, CSRF, CORS, and other web-security rules. |
| `HttpSecurity` | `authorizeHttpRequests()`, `oauth2Login()`, `formLogin()`, `csrf()`, `cors()` | Fluent builder used to configure Servlet security. |
| `Authentication` | `getPrincipal()`, `getName()`, `getAuthorities()` | Represents the current authenticated identity. |
| `UserDetails` | `getUsername()`, `getPassword()`, `getAuthorities()` | Standard principal contract for username/password authentication. |
| `OidcUser` | `getSubject()`, `getEmail()`, `getFullName()`, `getClaims()` | Principal returned by OpenID Connect login providers such as Google. |
| `SecurityContextHolder` | `getContext()` | Gives non-controller code access to the current security context. Prefer injecting the principal where practical. |

Example security configuration:

```java
@Bean
SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/", "/{shortCode}").permitAll()
                    .anyRequest().authenticated()
            )
            .oauth2Login(Customizer.withDefaults());

    return http.build();
}
```

## Services and repositories

| Class or interface | Useful methods | Purpose |
| --- | --- | --- |
| `CrudRepository<T, ID>` | `save()`, `findById()`, `findAll()`, `deleteById()`, `existsById()` | Provides basic repository operations. |
| `JpaRepository<T, ID>` | CRUD methods plus `flush()`, paging, and sorting | Common Spring Data JPA repository base interface. |
| `Optional<T>` | `orElseThrow()`, `map()`, `orElseGet()` | Represents a database lookup that might not return a value. |
| `Pageable` | `getPageNumber()`, `getPageSize()`, `getSort()` | Describes requested pagination and sorting. |
| `Page<T>` | `getContent()`, `getTotalElements()`, `getTotalPages()` | Contains one page of results and pagination metadata. |

Example repository:

```java
public interface UrlRepository extends JpaRepository<Url, Long> {
    Optional<Url> findByShortCode(String shortCode);
    Page<Url> findByOwnerId(Long ownerId, Pageable pageable);
}
```

Example service method:

```java
@Transactional(readOnly = true)
public UrlResponse findByShortCode(String shortCode) {
    Url url = urlRepository.findByShortCode(shortCode)
            .orElseThrow(() -> new ResourceNotFoundException("URL not found"));

    return urlMapper.toResponse(url);
}
```

Keep business logic in services rather than controllers. Controllers should mainly read HTTP input, call a service, and construct the response.

## Calling another HTTP service

| Class or interface | Useful methods | Purpose |
| --- | --- | --- |
| `RestClient` | `get()`, `post()`, `uri()`, `header()`, `body()`, `retrieve()` | Current synchronous fluent HTTP client for application code. |
| `WebClient` | `get()`, `post()`, `uri()`, `retrieve()`, `bodyToMono()` | Non-blocking HTTP client, mainly useful in reactive applications. |
| `ParameterizedTypeReference<T>` | Anonymous subclass | Preserves generic response type information such as `List<UserResponse>`. |

Create a reusable `RestClient` bean:

```java
@Bean
RestClient userApi(RestClient.Builder builder) {
    return builder
            .baseUrl("https://api.example.com")
            .build();
}
```

Use it from a service:

```java
UserResponse user = userApi.get()
        .uri("/users/{id}", id)
        .retrieve()
        .body(UserResponse.class);
```

Do not create a new HTTP client for every request. Configure and inject a reusable client.

## File upload and download

| Class or interface | Useful methods | Purpose |
| --- | --- | --- |
| `MultipartFile` | `getOriginalFilename()`, `getContentType()`, `getSize()`, `isEmpty()`, `getInputStream()`, `transferTo()` | Represents an uploaded multipart file. |
| `Resource` | `getInputStream()`, `getFilename()`, `contentLength()` | General interface for downloadable content. |
| `FileSystemResource` | — | Exposes a filesystem file as a Spring `Resource`. |
| `ByteArrayResource` | — | Exposes in-memory bytes as a Spring `Resource`. |
| `ContentDisposition` | `attachment()`, `filename()`, `build()` | Builds a safe `Content-Disposition` header for downloads. |

Upload example:

```java
@PostMapping(path = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<Void> uploadAvatar(@RequestPart MultipartFile file) {
    avatarService.store(file);
    return ResponseEntity.noContent().build();
}
```

Download example:

```java
Resource file = fileService.load(id);

return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment()
                        .filename(file.getFilename())
                        .build()
                        .toString()
        )
        .body(file);
```

Validate file size, content type, and generated storage path. Do not trust the original filename as a filesystem path.

## Controller and HTTP testing

| Class or interface | Useful methods | Purpose |
| --- | --- | --- |
| `MockMvc` | `perform()` | Tests Spring MVC endpoints without starting a real HTTP server. |
| `MockMvcRequestBuilders` | `get()`, `post()`, `put()`, `delete()` | Builds mock HTTP requests. Usually used through static imports. |
| `MockMvcResultMatchers` | `status()`, `content()`, `jsonPath()`, `header()` | Verifies mock responses. Usually used through static imports. |
| `RestTestClient` | `get()`, `post()`, `exchange()`, `expectStatus()`, `expectBody()` | Fluent Spring Boot client for testing HTTP endpoints, with or without a running server. |
| `WebTestClient` | `get()`, `post()`, `exchange()`, `expectStatus()`, `expectBody()` | Reactive test client that can also test Spring MVC applications. |
| `TestRestTemplate` | `getForEntity()`, `postForEntity()`, `exchange()` | Convenient blocking client for tests against a running server. |

`MockMvc` example:

```java
mockMvc.perform(get("/api/urls/{id}", 17))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(17));
```

Use `MockMvc` for focused MVC tests. Use `RestTestClient`, `WebTestClient`, or `TestRestTemplate` when the test should make requests through a running server.

## Frequently used Java types

| Type | Purpose in HTTP applications |
| --- | --- |
| `Optional<T>` | Models a value that might be absent, commonly from repository lookups. |
| `List<T>` / `Set<T>` | Models collections in request and response DTOs. |
| `Map<K, V>` | Useful for small dynamic structures; prefer DTOs for stable API contracts. |
| `Instant` | Represents a precise UTC timestamp. |
| `Duration` | Represents timeouts, cache lifetimes, and cookie ages. |
| `UUID` | Generates non-sequential identifiers when the domain requires them. |
| `URI` | Represents validated URI syntax for redirects and client calls. |

## A typical request flow

```text
HTTP request
    -> Controller reads and validates a request DTO
    -> Service applies business rules
    -> Repository reads or writes database entities
    -> Service maps the result to a response DTO
    -> Controller returns ResponseEntity
```

Example:

```java
@PutMapping("/profile")
public ResponseEntity<ProfileResponse> updateProfile(
        Authentication authentication,
        @Valid @RequestBody UpdateProfileRequest request
) {
    ProfileResponse updated = profileService.update(
            authentication.getName(),
            request
    );

    return ResponseEntity.ok(updated);
}
```

## Official references

- [Spring MVC controller method arguments](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/arguments.html)
- [Spring MVC return values](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/return-types.html)
- [Spring `ResponseEntity`](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/responseentity.html)
- [Spring REST clients](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html)
- [Spring MVC testing](https://docs.spring.io/spring-framework/reference/testing/mockmvc.html)
- [Spring Boot testing](https://docs.spring.io/spring-boot/reference/testing/index.html)
- [Spring Security authentication architecture](https://docs.spring.io/spring-security/reference/servlet/authentication/architecture.html)
