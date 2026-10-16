package com.shrtrl.urls.service;

import com.shrtrl.services.UrlService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class UrlServicePropertyTest {

    private static final String BASE62_ALPHABET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final Duration SHORTEN_TIMEOUT = Duration.ofMillis(250);

    private final UrlService urlService = new UrlService(null);

    @Property(tries = 100)
    void shorteningPositiveIdsTerminatesAndProducesBase62(
            @ForAll @IntRange(min = 1) int id) {

        String shortCode = runWithTimeout(() -> urlService.shorten(id));

        assertFalse(shortCode.isEmpty());
        assertTrue(shortCode.chars()
                .allMatch(character -> BASE62_ALPHABET.indexOf(character) >= 0));
    }

    @Property(tries = 100)
    void shorteningIsDeterministic(@ForAll @IntRange(min = 0) int id) {
        String[] results = runWithTimeout(() -> new String[] {
                urlService.shorten(id),
                urlService.shorten(id)
        });

        assertEquals(results[0], results[1]);
    }

    private static <T> T runWithTimeout(Callable<T> operation) {
        ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "base62-property-test");
            thread.setDaemon(true);
            return thread;
        });

        Future<T> future = executor.submit(operation);
        try {
            return future.get(SHORTEN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            fail("shorten(int) did not terminate within " + SHORTEN_TIMEOUT, exception);
            return null;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Property test was interrupted", exception);
        } catch (ExecutionException exception) {
            throw new AssertionError("shorten(int) failed", exception.getCause());
        } finally {
            executor.shutdownNow();
        }
    }
}
