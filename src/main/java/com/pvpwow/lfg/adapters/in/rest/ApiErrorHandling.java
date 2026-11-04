package com.pvpwow.lfg.adapters.in.rest;

import com.pvpwow.lfg.application.error.ResourceNotFoundException;
import com.pvpwow.lfg.application.error.UnauthorizedException;
import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * The error strategy, as code. The domain stays silent; the edge speaks:
 * this is the first slf4j Logger in the codebase, and it lives exactly where
 * the strategy put it.
 *
 * <ul>
 *   <li>Domain rule violations are NORMAL BUSINESS: the caller gets the
 *       message as 422 ProblemDetail, logged at DEBUG — never ERROR, or bot
 *       traffic buries real signals.</li>
 *   <li>A missing resource and a resource the caller may not touch are both
 *       404 with the same detail, so the edge never reveals existence.</li>
 *   <li>Unauthorized (dev stub): 401.</li>
 *   <li>Malformed body or a cursor this API did not issue: 400.</li>
 *   <li>Bean Validation failures (a missing field): 422, same family as a
 *       domain rule violation. The framework's default would be 400, but a
 *       missing field is invalid input the caller can fix, not a malformed
 *       request.</li>
 *   <li>Everything else is an anomaly: 500, generic detail, NO extra
 *       properties, no exception class, no stack trace (the trace lives in the
 *       ERROR log, for the operator). Spring's own framework errors (405,
 *       406...) already know their status — they pass through untouched.</li>
 * </ul>
 */
@RestControllerAdvice
final class ApiErrorHandling {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorHandling.class);

    @ExceptionHandler(DomainRuleViolationException.class)
    ProblemDetail domainRuleViolated(DomainRuleViolationException violation) {
        log.debug("Rejected a request: {}", violation.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, violation.getMessage());
        problem.setTitle("Domain rule violation");
        return problem;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail resourceNotFound(ResourceNotFoundException notFound) {
        log.debug("Missing resource: {}", notFound.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, notFound.getMessage());
        problem.setTitle("Resource not found");
        return problem;
    }

    @ExceptionHandler(UnauthorizedException.class)
    ProblemDetail unauthorized(UnauthorizedException unauthorized) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, unauthorized.getMessage());
        problem.setTitle("Unauthorized");
        return problem;
    }

    @ExceptionHandler(MalformedCursorException.class)
    ProblemDetail malformedCursor(MalformedCursorException malformed) {
        log.debug("Rejected a malformed cursor");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, malformed.getMessage());
        problem.setTitle("Malformed request");
        return problem;
    }

    @ExceptionHandler(MalformedIdentifierException.class)
    ProblemDetail malformedIdentifier(MalformedIdentifierException malformed) {
        log.debug("Rejected a malformed identifier");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, malformed.getMessage());
        problem.setTitle("Malformed request");
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail malformedBody(HttpMessageNotReadableException malformed) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Malformed request body");
        problem.setTitle("Malformed request");
        return problem;
    }

    /**
     * Bean Validation failures are NORMAL BUSINESS, exactly like a domain rule
     * violation: the caller sent something invalid and must be told which
     * field. Spring would answer 400 with the field errors in the body; this
     * API answers 422 with the same wording the hand-written guards used, so
     * the whole "invalid input" family speaks one language.
     *
     * <p>The wording comes from the violation path, not from the message
     * bundle, so it does not depend on the JVM's locale: a Spanish JVM must
     * not answer a Spanish error to an English API.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidBody(MethodArgumentNotValidException invalid) {
        String detail = invalid.getBindingResult().getAllErrors().stream()
                .filter(FieldError.class::isInstance)
                .map(FieldError.class::cast)
                .map(error -> "%s is required".formatted(error.getField()))
                .distinct()
                .collect(Collectors.joining(", "));
        if (detail.isEmpty()) {
            detail = "Request body is invalid";
        }
        log.debug("Rejected a request: {}", detail);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, detail);
        problem.setTitle("Invalid request");
        return problem;
    }

    /**
     * A constraint violation outside a request body (a path variable, a request
     * parameter). Same family: invalid input the caller can fix.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail constraintViolated(ConstraintViolationException violation) {
        String detail = WirePresence.describe(violation);
        log.debug("Rejected a request: {}", detail);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, detail);
        problem.setTitle("Invalid request");
        return problem;
    }

    /**
     * The last resort: anything unexpected is an anomaly.
     *
     * <p>The response is deliberately BLANK: a generic detail, no extra
     * properties, no exception class, no message, no stack trace. The stack
     * trace belongs in the ERROR log, where the operator reads it — never in
     * the response, where whoever sent the request reads it. The request may
     * be hostile; the response tells it nothing about the inside.
     *
     * <p>Spring's own framework errors (405, 406...) already know their
     * status and their body; they pass through untouched.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception failure) {
        if (failure instanceof ErrorResponse frameworkHandled) {
            return ResponseEntity.status(frameworkHandled.getStatusCode())
                    .body(frameworkHandled.getBody());
        }
        log.error("Unexpected failure", failure);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
        problem.setTitle("Internal error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }
}
