package com.shrtrl.interfaces.urls;

import com.shrtrl.DTOs.UrlCreateResponse;

public sealed interface UrlShortenResult {
    record NoUserFound() implements UrlShortenResult {}
    record NoGroupFound() implements UrlShortenResult {}
    record UrlShortened(UrlCreateResponse response) implements UrlShortenResult {}
    record DatabaseFailure(RuntimeException cause) implements UrlShortenResult {}
    record RuntimeFailure(RuntimeException cause) implements UrlShortenResult {}
}
