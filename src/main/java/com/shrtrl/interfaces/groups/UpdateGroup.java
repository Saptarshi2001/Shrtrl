package com.shrtrl.interfaces.groups;

public sealed interface UpdateGroup {
    record NoGroupFound() implements UpdateGroup {}
    record DatabaseFailure(RuntimeException cause) implements UpdateGroup {}
    record RuntimeFailure(RuntimeException cause) implements UpdateGroup {}
    record GroupUpdated() implements UpdateGroup {}
}
