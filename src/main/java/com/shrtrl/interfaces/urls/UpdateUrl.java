package com.shrtrl.interfaces.urls;

public sealed interface UpdateUrl {
    record NoShortCodeFound() implements UpdateUrl {}
    record UrlUpdated() implements UpdateUrl {}
    record DatabaseFailure(RuntimeException cause) implements UpdateUrl {}
    record RuntimeFailure(RuntimeException cause) implements UpdateUrl {}
}
