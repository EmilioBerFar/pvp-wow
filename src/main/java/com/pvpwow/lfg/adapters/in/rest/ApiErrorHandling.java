package com.pvpwow.lfg.adapters.in.rest;

import com.pvpwow.lfg.adapters.out.security.UnauthorizedException;
import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The error strategy, as code. The domain stays silent; the edge speaks:
 * this is the first slf4j Logger in the codebase, and it lives exactly where
 * the strategy put it.
 *
 * <ul>
 *   <li>Domain rule violations are NORMAL BUSINESS: the caller gets the
 *       message as 422 ProblemDetail, logged at DEBUG — never ERROR, or bot
 *       traffic buries real signals.</li>
 *   <li>Unauthorized (dev stub): 401.</li>
 *   <li>Malformed body: 400.</li>
 *   <li>Everything else is an anomaly: 500, generic detail (no internals
 *       leak), ERROR log. Spring's own framework errors (405, 406...) already
 *       know their status — they pass through untouched.</li>
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

    @ExceptionHandler(UnauthorizedException.class)
    ProblemDetail unauthorized(UnauthorizedException unauthorized) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, unauthorized.getMessage());
        problem.setTitle("Unauthorized");
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail malformedBody(HttpMessageNotReadableException malformed) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Malformed request body");
        problem.setTitle("Malformed request");
        return problem;
    }

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
