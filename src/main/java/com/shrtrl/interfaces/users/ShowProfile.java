package com.shrtrl.interfaces.users;

import com.shrtrl.DTOs.UserProfileResponse;

public sealed interface ShowProfile {
    record ProfileFound(UserProfileResponse profile) implements ShowProfile {}
    record NoUserFound() implements ShowProfile {}
    record DatabaseFailure(RuntimeException cause) implements ShowProfile {}
    record RuntimeFailure(RuntimeException cause) implements ShowProfile {}
}
