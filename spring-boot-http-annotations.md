# Spring Boot HTTP Application Annotations

## Application and configuration

| Annotation | Purpose |
| --- | --- |
| `@SpringBootApplication` | Marks the main application class and enables auto-configuration and component scanning. |
| `@Configuration` | Declares a class that provides Spring configuration. |
| `@Bean` | Registers an object returned by a method as a Spring-managed bean. |
| `@Value` | Injects a value from configuration or an environment variable. |
| `@ConfigurationProperties` | Binds a group of configuration properties to a Java class. |

## Components and dependency injection

| Annotation | Purpose |
| --- | --- |
| `@Component` | Marks a general Spring-managed class. |
| `@Controller` | Marks an MVC controller that usually returns view names. |
| `@RestController` | Marks an HTTP API controller whose methods return response bodies. It combines `@Controller` and `@ResponseBody`. |
| `@Service` | Marks a service-layer class. |
| `@Repository` | Marks a database-access class and enables persistence exception translation. |
| `@Autowired` | Injects a dependency. It is unnecessary on a class's only constructor. |
| `@Qualifier` | Selects a specific bean when multiple beans have the same type. |
| `@Primary` | Makes one bean the default when multiple beans have the same type. |

## Request routing

| Annotation | HTTP method | Purpose |
| --- | --- | --- |
| `@RequestMapping` | Any/configurable | Defines a base path or a customizable request mapping. |
| `@GetMapping` | `GET` | Reads a resource. |
| `@PostMapping` | `POST` | Creates a resource or submits an action. |
| `@PutMapping` | `PUT` | Completely replaces or updates a resource. |
| `@PatchMapping` | `PATCH` | Partially updates a resource. |
| `@DeleteMapping` | `DELETE` | Deletes a resource. |

Example:

```java
@RestController
@RequestMapping("/api/urls")
public class UrlController {

    @GetMapping("/{id}")
    public UrlResponse getUrl(@PathVariable Long id) {
        // ...
    }
}
```

## Reading request data

| Annotation | Purpose | Example input |
| --- | --- | --- |
| `@PathVariable` | Reads a value embedded in the URL path. | `/users/17` |
| `@RequestParam` | Reads a query parameter or form field. | `/search?page=2` |
| `@RequestBody` | Converts a JSON/XML request body into a Java object. | `{"username":"sam"}` |
| `@RequestHeader` | Reads an HTTP request header. | `Authorization: Bearer ...` |
| `@CookieValue` | Reads a cookie value. | `JSESSIONID=...` |
| `@RequestPart` | Reads one part of a multipart request, such as a file. | File upload |
| `@ModelAttribute` | Binds form fields or query parameters to a Java object. | HTML form submission |
| `@SessionAttribute` | Reads an existing HTTP session attribute. | `USER_ID` |
| `@AuthenticationPrincipal` | Injects the currently authenticated Spring Security principal. | Logged-in user |

Examples:

```java
@GetMapping("/{shortCode}")
public UrlResponse getUrl(
        @PathVariable String shortCode,
        @RequestParam(defaultValue = "false") boolean includeStats,
        @RequestHeader(value = "User-Agent", required = false) String userAgent
) {
    // ...
}
```

```java
@PostMapping
public UrlResponse createUrl(@Valid @RequestBody CreateUrlRequest request) {
    // ...
}
```

## Responses and status codes

| Annotation | Purpose |
| --- | --- |
| `@ResponseBody` | Writes a controller method's return value into the HTTP response body. Already included by `@RestController`. |
| `@ResponseStatus` | Sets the HTTP status returned by a method or exception. |

Example:

```java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public UrlResponse createUrl(@RequestBody CreateUrlRequest request) {
    // ...
}
```

Use `ResponseEntity<T>` when the status or headers must be chosen dynamically:

```java
return ResponseEntity.status(HttpStatus.CREATED).body(response);
```

## Validation

| Annotation | Purpose |
| --- | --- |
| `@Valid` | Validates a request object using Jakarta Bean Validation annotations. |
| `@Validated` | Enables Spring validation and supports validation groups. |
| `@NotNull` | Rejects `null`. |
| `@NotBlank` | Rejects `null`, empty, and whitespace-only strings. |
| `@NotEmpty` | Rejects `null` and empty strings/collections. |
| `@Size` | Restricts string, collection, map, or array size. |
| `@Min` / `@Max` | Restricts numeric values. |
| `@Positive` / `@PositiveOrZero` | Requires a positive or non-negative number. |
| `@Email` | Checks that a string has a valid email format. |
| `@Pattern` | Checks a string using a regular expression. |

Example:

```java
public record CreateUserRequest(
        @NotBlank @Size(max = 50) String username,
        @NotBlank @Email String email
) {}
```

## Exception handling

| Annotation | Purpose |
| --- | --- |
| `@ExceptionHandler` | Handles selected exception types in a controller or advice class. |
| `@ControllerAdvice` | Applies shared controller behavior, such as exception handling. |
| `@RestControllerAdvice` | Combines `@ControllerAdvice` and `@ResponseBody` for REST APIs. |

Example:

```java
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(ResourceNotFoundException exception) {
        return new ErrorResponse(exception.getMessage());
    }
}
```

## Cross-origin requests

| Annotation | Purpose |
| --- | --- |
| `@CrossOrigin` | Allows configured origins to call a controller or endpoint from a browser. |

Example:

```java
@CrossOrigin(origins = "http://localhost:3000")
@RestController
public class UrlController {
    // ...
}
```

For a production application, centralized CORS configuration is usually preferable to repeating `@CrossOrigin`.

## Spring Security

| Annotation | Purpose |
| --- | --- |
| `@EnableWebSecurity` | Enables and customizes Spring Security's web-security configuration. |
| `@EnableMethodSecurity` | Enables annotations such as `@PreAuthorize`. |
| `@PreAuthorize` | Checks authorization before invoking a method. |
| `@PostAuthorize` | Checks authorization after a method returns. |
| `@AuthenticationPrincipal` | Injects the authenticated principal into a controller method. |

Example:

```java
@PreAuthorize("hasRole('ADMIN')")
@DeleteMapping("/{id}")
public void deleteUser(@PathVariable Long id) {
    // ...
}
```

`@EnableMethodSecurity` must be enabled before using `@PreAuthorize` or `@PostAuthorize`.

## Persistence annotations commonly used by HTTP applications

| Annotation | Purpose |
| --- | --- |
| `@Entity` | Marks a JPA database entity. |
| `@Table` | Configures the database table used by an entity. |
| `@Id` | Marks the entity's primary-key field. |
| `@GeneratedValue` | Configures automatic primary-key generation. |
| `@Column` | Configures a database column. |
| `@OneToOne` | Defines a one-to-one relationship. |
| `@OneToMany` | Defines a one-to-many relationship. |
| `@ManyToOne` | Defines a many-to-one relationship. |
| `@ManyToMany` | Defines a many-to-many relationship. |
| `@Transactional` | Runs a method or class inside a database transaction. |

## Testing annotations

| Annotation | Purpose |
| --- | --- |
| `@SpringBootTest` | Loads the complete Spring Boot application context for a test. |
| `@WebMvcTest` | Loads only the Spring MVC layer for controller tests. |
| `@DataJpaTest` | Loads JPA components for repository tests. |
| `@Test` | Marks a JUnit test method. |

## Official references

- [Spring MVC annotated controllers](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller.html)
- [Spring MVC request mappings](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html)
- [Spring MVC controller method arguments](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/arguments.html)
- [Spring annotation-based configuration](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config.html)
- [Spring Security method security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html)
