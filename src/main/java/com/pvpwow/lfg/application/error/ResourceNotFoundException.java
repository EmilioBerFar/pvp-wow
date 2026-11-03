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

    /**
     * "No such character" and "not your character" are the SAME answer.
     *
     * <p>A factory instead of a string constant at each call site: an
     * anti-enumeration rule that has to be re-typed everywhere will eventually
     * diverge, and the moment one path says "not yours" the caller can map
     * every character in the game (OWASP anti-enumeration).
     */
    public static ResourceNotFoundException characterNotFound() {
        return new ResourceNotFoundException("Character not found");
    }
}
