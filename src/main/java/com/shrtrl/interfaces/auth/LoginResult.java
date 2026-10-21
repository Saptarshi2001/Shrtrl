package com.shrtrl.interfaces.auth;

import com.shrtrl.models.Users;

public sealed interface LoginResult {
    record AuthenticatedUser(Users user) implements LoginResult {}
    record InvalidCredentials() implements LoginResult {}
    record DatabaseFailure(RuntimeException cause) implements LoginResult {}
    record RuntimeFailure(RuntimeException cause) implements LoginResult {}
}
