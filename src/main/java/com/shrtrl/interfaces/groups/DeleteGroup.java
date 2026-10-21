package com.shrtrl.interfaces.groups;

public sealed interface DeleteGroup {
    record NoGroupFound() implements DeleteGroup {}
    record DatabaseFailure(RuntimeException cause) implements DeleteGroup {}
    record RuntimeFailure(RuntimeException cause) implements DeleteGroup {}
    record GroupDeleted() implements DeleteGroup {}
}
