package com.shrtrl.urls.dto;

import com.shrtrl.DTOs.UrlCreateRequest;

import com.shrtrl.DTOs.UpdateLongUrl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class UrlRequestPropertyTest {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Property(tries = 100)
    void generatedHttpAndHttpsUrlsAreAccepted(
            @ForAll("validHttpUrls") String longUrl) {

        assertValid(new UrlCreateRequest(longUrl));
        assertValid(new UpdateLongUrl(longUrl));
    }

    @Property(tries = 100)
    void stringsWithoutHttpOrHttpsSchemeAreRejected(
            @ForAll("valuesWithoutScheme") String longUrl) {

        assertInvalid(new UrlCreateRequest(longUrl));
        assertInvalid(new UpdateLongUrl(longUrl));
    }

    @Property
    void blankValuesAreRejected(@ForAll("blankValues") String longUrl) {
        assertInvalid(new UrlCreateRequest(longUrl));
        assertInvalid(new UpdateLongUrl(longUrl));
    }

    @Property(tries = 100)
    void valuesLongerThan2048CharactersAreRejected(
            @ForAll("oversizedHttpUrls") String longUrl) {

        assertTrue(longUrl.length() > 2048);
        assertInvalid(new UrlCreateRequest(longUrl));
        assertInvalid(new UpdateLongUrl(longUrl));
    }

    @Provide
    Arbitrary<String> validHttpUrls() {
        Arbitrary<String> schemes = Arbitraries.of("http", "https");
        Arbitrary<String> hostLabels = Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1)
                .ofMaxLength(30);
        Arbitrary<String> paths = Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(0)
                .ofMaxLength(100);

        return Combinators.combine(schemes, hostLabels, paths)
                .as((scheme, host, path) ->
                        scheme + "://" + host + ".example/" + path);
    }

    @Provide
    Arbitrary<String> valuesWithoutScheme() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1)
                .ofMaxLength(200);
    }

    @Provide
    Arbitrary<String> blankValues() {
        return Arbitraries.of("", " ", "\t", "\n", "\r\n", " \t ");
    }

    @Provide
    Arbitrary<String> oversizedHttpUrls() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(2030)
                .ofMaxLength(2100)
                .map(path -> "https://example.com/" + path);
    }

    private void assertValid(Object request) {
        Set<ConstraintViolation<Object>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), () -> "Unexpected violations: " + violations);
    }

    private void assertInvalid(Object request) {
        Set<ConstraintViolation<Object>> violations = validator.validate(request);
        assertFalse(violations.isEmpty(), "Expected request to be rejected");
    }
}
