package com.pvpwow.lfg.adapters.in.rest;

import com.pvpwow.lfg.application.port.in.AcceptJoinRequest;
import com.pvpwow.lfg.domain.join.JoinRequestId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The listing owner accepts a join request: a group forms and every active
 * listing of both players closes.
 */
@RestController
@RequestMapping("/api/join-requests/{joinRequestId}")
public final class AcceptJoinRequestController {

    private final AcceptJoinRequest acceptJoinRequest;

    public AcceptJoinRequestController(AcceptJoinRequest acceptJoinRequest) {
        this.acceptJoinRequest = acceptJoinRequest;
    }

    @PostMapping("/accept")
    ResponseEntity<Void> accept(@PathVariable String joinRequestId) {
        acceptJoinRequest.accept(new JoinRequestId(uuid(joinRequestId)));
        return ResponseEntity.noContent().build();
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException malformed) {
            throw new MalformedIdentifierException("Malformed identifier");
        }
    }
}
