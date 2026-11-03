package com.pvpwow.lfg.adapters.in.rest;

/**
 * A cursor whose base64 payload is not the one this API issued. It is a
 * wire-level failure, so it maps to 400: the client must echo the token
 * verbatim, and a hand-built token is a malformed request, not a business
 * rule violation.
 */
final class MalformedCursorException extends RuntimeException {

    MalformedCursorException() {
        super("Malformed cursor");
    }
}
