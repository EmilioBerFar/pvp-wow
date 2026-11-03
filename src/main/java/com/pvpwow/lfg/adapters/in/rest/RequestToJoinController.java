package com.pvpwow.lfg.adapters.in.rest;

import com.pvpwow.lfg.application.port.in.RequestToJoin;
import com.pvpwow.lfg.application.port.in.RequestToJoin.RequestToJoinCommand;
import com.pvpwow.lfg.domain.character.CharacterId;
import com.pvpwow.lfg.domain.join.JoinRequestId;
import com.pvpwow.lfg.domain.listing.ListingId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Ask to join somebody else's listing. Nothing closes here: the owner's
 * answer is what closes listings.
 */
@RestController
@RequestMapping("/api/listings/{listingId}/join-requests")
public final class RequestToJoinController {

    private final RequestToJoin requestToJoin;

    public RequestToJoinController(RequestToJoin requestToJoin) {
        this.requestToJoin = requestToJoin;
    }

    @PostMapping
    ResponseEntity<JoinRequestResponse> request(
            @PathVariable String listingId,
            @Valid @RequestBody JoinRequestBody body) {

        JoinRequestId id = requestToJoin.request(new RequestToJoinCommand(
                new CharacterId(uuid(body.characterId())),
                new ListingId(uuid(listingId))));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new JoinRequestResponse(id.value()));
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException malformed) {
            throw new MalformedIdentifierException("Malformed identifier");
        }
    }

    record JoinRequestBody(@NotNull @NotBlank String characterId) {
    }

    record JoinRequestResponse(UUID id) {
    }
}
