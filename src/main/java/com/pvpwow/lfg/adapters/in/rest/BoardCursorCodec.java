package com.pvpwow.lfg.adapters.in.rest;

import com.pvpwow.lfg.application.port.in.SearchBoard.BoardCursor;
import com.pvpwow.lfg.domain.listing.ListingId;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

/**
 * Opaque cursor for the board's keyset pagination.
 *
 * <p>Opaque on purpose: the client echoes the token, it never composes one.
 * That keeps the {@code (createdAt, id)} shape free to change without breaking
 * callers, and it prevents a client from inventing a position that the SQL
 * order does not understand. The version prefix allows a future cursor format
 * to be rejected cleanly instead of misread.
 */
final class BoardCursorCodec {

    private static final String VERSION = "1";
    private static final String SEPARATOR = "|";
    private static final int PARTS = 3;

    private BoardCursorCodec() {
    }

    static String encode(BoardCursor cursor) {
        String raw = VERSION + SEPARATOR + cursor.createdAt() + SEPARATOR
                + cursor.listingId().value();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    static BoardCursor decode(String encoded) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded),
                    StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", -1);
            if (parts.length != PARTS || !VERSION.equals(parts[0])) {
                throw new MalformedCursorException();
            }
            return new BoardCursor(Instant.parse(parts[1]),
                    new ListingId(UUID.fromString(parts[2])));
        } catch (IllegalArgumentException | DateTimeParseException malformed) {
            throw new MalformedCursorException();
        }
    }
}
