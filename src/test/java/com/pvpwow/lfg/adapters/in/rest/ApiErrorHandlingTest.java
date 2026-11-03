package com.pvpwow.lfg.adapters.in.rest;

import com.pvpwow.lfg.application.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The status mapping for application failures that are NOT domain rule
 * violations. The REST slice proves the wiring in WU12; this locks the
 * contract the advice promises.
 */
class ApiErrorHandlingTest {

    private final ApiErrorHandling advice = new ApiErrorHandling();

    @Test
    void aMissingResourceIs404() {
        ProblemDetail problem = advice.resourceNotFound(
                new ResourceNotFoundException("Character not found"));

        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getTitle()).isEqualTo("Resource not found");
        assertThat(problem.getDetail()).isEqualTo("Character not found");
    }

    /**
     * The generic 500 must say NOTHING about what failed. A stack trace, an
     * exception class, a SQL fragment or a file path in the response body is
     * a leak of internals to whoever sent the request — and the request may
     * be hostile.
     */
    @Test
    void anUnexpectedFailureIsAGeneric500WithNoInternals() {
        ProblemDetail problem = advice.unexpected(
                new IllegalStateException("connection to jdbc:postgresql://localhost/pvpwow failed"))
                .getBody();

        assertThat(problem).isNotNull();
        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getTitle()).isEqualTo("Internal error");
        assertThat(problem.getDetail()).isEqualTo("Unexpected error");
        assertThat(problem.getProperties())
                .as("no extra properties may carry internals")
                .isNullOrEmpty();

        String body = problem.toString();
        assertThat(body)
                .doesNotContain("IllegalStateException")
                .doesNotContain("jdbc:postgresql")
                .doesNotContain("localhost")
                .doesNotContain("pvpwow")
                .doesNotContain("at com.pvpwow");
    }

    /**
     * The detail is the same generic phrase for every anomaly: a caller that
     * learns WHICH failure happened learns something about the server's
     * internals for free.
     */
    @Test
    void everyAnomalyGetsTheSameGenericDetail() {
        ProblemDetail first = advice.unexpected(new IllegalStateException("secret one"))
                .getBody();
        ProblemDetail second = advice.unexpected(new NullPointerException("secret two"))
                .getBody();

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(first.getDetail()).isEqualTo(second.getDetail());
        assertThat(first.getDetail()).doesNotContain("secret");
    }
}
