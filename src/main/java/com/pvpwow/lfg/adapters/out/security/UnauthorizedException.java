package com.pvpwow.lfg.adapters.out.security;

/**
 * The dev auth stub could not resolve who is calling. The advice translates
 * it to 401.
 */
public final class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
