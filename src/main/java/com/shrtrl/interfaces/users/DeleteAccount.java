package com.shrtrl.interfaces.users;

public sealed interface DeleteAccount {
    record AccountDeleted() implements DeleteAccount {}
    record NoUserFound() implements DeleteAccount {}
    record DatabaseFailure(RuntimeException cause) implements DeleteAccount {}
    record RuntimeFailure(RuntimeException cause) implements DeleteAccount {}
}
