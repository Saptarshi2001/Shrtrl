# Spring Session and Security Context Explained

This code runs after the submitted email and password have already been verified. Its purpose is to tell Spring Security that the user is authenticated and to preserve that authentication for future requests.

```java
HttpSession session = servletRequest.getSession();
servletRequest.changeSessionId();

SecurityContext context = SecurityContextHolder.createEmptyContext();
context.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
                user.getEmail(),
                null,
                List.of()
        )
);

SecurityContextHolder.setContext(context);
session.setAttribute(
        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
        context
);

session.setAttribute("USER_ID", user.getUserId());
```

## 1. Get or create the HTTP session

```java
HttpSession session = servletRequest.getSession();
```

`getSession()` returns the current HTTP session. If a session does not exist, the server creates one.

The server identifies the session with an ID. It normally sends that ID to the browser in a cookie:

```http
Set-Cookie: JSESSIONID=ABC123
```

The browser sends the cookie with later requests:

```http
Cookie: JSESSIONID=ABC123
```

This allows the server to find the same session again. Conceptually, the session can contain multiple attributes:

```text
Session
├── ID: ABC123
├── USER_ID: the application's database user UUID
└── SPRING_SECURITY_CONTEXT: Spring Security authentication information
```

## 2. Change the session ID after login

```java
servletRequest.changeSessionId();
```

This replaces the session identifier after login while preserving the session's contents:

```text
Before login: JSESSIONID=ABC123
After login:  JSESSIONID=XYZ789
```

This protects against a session-fixation attack. In such an attack, an attacker tries to make a victim use a session ID the attacker already knows. If the victim logs in without the session ID changing, the attacker may try to reuse that authenticated session ID.

Changing the ID after successful login invalidates the old identifier:

```text
Attacker knows ABC123
        ↓
Victim successfully logs in
        ↓
Server changes ABC123 to XYZ789
        ↓
Attacker's old ABC123 is no longer the authenticated session ID
```

## 3. Create an empty `SecurityContext`

```java
SecurityContext context = SecurityContextHolder.createEmptyContext();
```

A `SecurityContext` is Spring Security's container for the currently authenticated user's security information:

```text
SecurityContext
└── Authentication
    ├── principal: who is logged in
    ├── credentials: proof such as a password
    ├── authorities: roles and permissions
    └── authenticated: whether authentication succeeded
```

At this point, the new context has no `Authentication` object yet.

## 4. Create the authenticated token

```java
UsernamePasswordAuthenticationToken.authenticated(
        user.getEmail(),
        null,
        List.of()
)
```

This creates an `Authentication` object that is already marked as authenticated. This is appropriate only because the application has already checked the submitted password against the stored password hash.

The arguments mean:

```java
user.getEmail() // principal: identifies the logged-in user
null            // credentials: do not retain the submitted password
List.of()       // authorities: no roles or permissions have been assigned
```

### Principal

The principal identifies the user to Spring Security. Here it is the email address:

```text
principal = alice@example.com
```

A future design could use a custom principal containing both the database UUID and email address.

### Credentials

The credentials value is `null` because the password has already been verified. The plain-text password should not be saved in the authentication object or HTTP session.

### Authorities

Authorities represent roles or permissions. `List.of()` means the user is authenticated but has no role authorities:

```text
Authenticated: yes
Authorities:   none
```

This user can pass a rule such as:

```java
.anyRequest().authenticated()
```

but cannot pass a role rule such as:

```java
.hasRole("ADMIN")
```

Roles could later be supplied with values such as `ROLE_USER` or `ROLE_ADMIN`.

## 5. Put authentication into the context

```java
context.setAuthentication(authentication);
```

In the original code, the token is created directly inside `setAuthentication(...)`. After this call, the context conceptually contains:

```text
SecurityContext
└── Authentication
    ├── principal: alice@example.com
    ├── credentials: null
    ├── authorities: []
    └── authenticated: true
```

## 6. Set the context for the current request

```java
SecurityContextHolder.setContext(context);
```

`SecurityContextHolder` makes the context available to Spring Security and application code during the current request.

For example, code running during that request can access:

```java
SecurityContextHolder.getContext().getAuthentication()
```

Setting the holder handles the current request. By itself, it does not preserve authentication for a later HTTP request.

## 7. Store the context under Spring Security's session key

```java
session.setAttribute(
        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
        context
);
```

An HTTP session is a key-value store. Spring Security needs a known key so it can find its `SecurityContext` among all the other session attributes.

`HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY` is Spring Security's standard key for this purpose. It corresponds to the session attribute name used by Spring Security for the security context.

The key is needed because the session may hold many unrelated values:

```text
Session attribute key       Session attribute value
-------------------------   -------------------------------------
USER_ID                     UUID used by the application
SPRING_SECURITY_CONTEXT     SecurityContext used by Spring Security
theme                       A possible UI preference
```

On the next request, Spring Security uses the known key to retrieve the correct value:

```text
JSESSIONID arrives
        ↓
Server loads the matching HttpSession
        ↓
Spring looks up SPRING_SECURITY_CONTEXT
        ↓
Spring restores the user's Authentication
```

Without saving the context under this key:

```text
Login request → authenticated in the current request
Next request  → authentication is not available from the session
```

With it saved:

```text
Login request
→ context stored in the session
→ browser retains JSESSIONID
→ next request sends JSESSIONID
→ Spring loads the context using its standard key
→ user remains authenticated
```

## 8. Store the application's `USER_ID`

```java
session.setAttribute("USER_ID", user.getUserId());
```

This line serves a different purpose from `SPRING_SECURITY_CONTEXT`.

`SPRING_SECURITY_CONTEXT` answers Spring Security's question:

> Is this request from an authenticated user, and what authorities does that user have?

`USER_ID` answers the application's question:

> Which database user owns the URLs, groups, or analytics requested by this session?

Controllers can retrieve the UUID like this:

```java
UUID userId = (UUID) session.getAttribute("USER_ID");
```

and pass it to ownership-aware database queries, for example:

```text
Find a URL where shortcode matches AND userid matches
```

Therefore, both attributes currently have a purpose:

```text
SPRING_SECURITY_CONTEXT
→ maintained and read by Spring Security
→ controls whether protected endpoints are accessible

USER_ID
→ maintained and read by this application
→ provides the UUID used for database lookups and ownership checks
```

`USER_ID` is not what makes Spring Security consider the caller authenticated. Likewise, the current principal contains only the email, so it does not directly provide the UUID needed by the existing controller code.

A later refactor could create a custom principal containing the UUID. Controllers could then retrieve the UUID from the authenticated principal, making the separate `USER_ID` session attribute unnecessary. With the current design, keeping both values is reasonable.

## Complete login and request flow

```text
Client submits email and password
        ↓
Application finds the user by email
        ↓
BCrypt verifies the submitted password against the stored hash
        ↓
Application gets or creates the HttpSession
        ↓
Session ID changes to protect against session fixation
        ↓
Application creates an empty SecurityContext
        ↓
Application creates an authenticated token
        ↓
Token is placed in the SecurityContext
        ↓
SecurityContextHolder receives it for the current request
        ↓
SecurityContext is stored under SPRING_SECURITY_CONTEXT
        ↓
Database UUID is stored separately under USER_ID
        ↓
Browser receives or updates its JSESSIONID cookie
        ↓
Browser sends JSESSIONID with a later protected request
        ↓
Spring restores SPRING_SECURITY_CONTEXT and permits authenticated routes
        ↓
Application reads USER_ID for database ownership queries
```

In short:

```text
JSESSIONID             → identifies which server-side session belongs to the browser
SPRING_SECURITY_CONTEXT → stores Spring Security's authenticated-user state
USER_ID                 → stores the UUID used by application database logic
```
