package com.shrtrl.interfaces.groups;

public sealed interface CreateGroup {
    record NoUserFound() implements CreateGroup {}
    record DatabaseFailure(RuntimeException cause) implements CreateGroup {}
    record RuntimeFailure(RuntimeException cause) implements CreateGroup {}
    record GroupCreated() implements CreateGroup {}
    record DuplicateGroup() implements CreateGroup{}
}
