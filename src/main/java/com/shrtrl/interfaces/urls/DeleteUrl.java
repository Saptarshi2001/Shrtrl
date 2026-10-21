package com.shrtrl.interfaces.urls;

public sealed interface DeleteUrl {
    record NoShortCodeFound() implements DeleteUrl {}
    record UrlDeleted() implements DeleteUrl {}
    record DatabaseFailure(RuntimeException cause) implements DeleteUrl {}
    record RuntimeFailure(RuntimeException cause) implements DeleteUrl {}
}
