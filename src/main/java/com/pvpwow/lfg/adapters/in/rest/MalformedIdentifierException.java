package com.pvpwow.lfg.adapters.in.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * A path variable or body field that is not a UUID. It cannot name a
 * resource, so it is a malformed request (400), not a missing one.
 */
final class MalformedIdentifierException extends RuntimeException {

    MalformedIdentifierException(String message) {
        super(message);
    }
}
