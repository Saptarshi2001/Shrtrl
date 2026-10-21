package com.shrtrl.interfaces.users;

public sealed interface UpdateAccount {
    record AccountUpdated() implements UpdateAccount {}
    record NoUserFound() implements UpdateAccount {}
    record DatabaseFailure(RuntimeException cause) implements UpdateAccount {}
    record RuntimeFailure(RuntimeException cause) implements UpdateAccount {}
}
