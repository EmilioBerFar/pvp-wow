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
}
