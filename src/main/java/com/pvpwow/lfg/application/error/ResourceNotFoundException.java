package com.pvpwow.lfg.application.error;

/**
 * The requested resource does not exist. Distinct from a domain rule
 * violation: the input was well formed, but the aggregate it names is absent.
 * The REST edge answers 404.
 */
public final class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
