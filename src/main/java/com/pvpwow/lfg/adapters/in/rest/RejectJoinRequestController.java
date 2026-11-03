package com.pvpwow.lfg.adapters.in.rest;

import com.pvpwow.lfg.application.port.in.RejectJoinRequest;
import com.pvpwow.lfg.domain.join.JoinRequestId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** The listing owner refuses a join request. Nothing closes. */
@RestController
@RequestMapping("/api/join-requests/{joinRequestId}")
public final class RejectJoinRequestController {

    private final RejectJoinRequest rejectJoinRequest;

    public RejectJoinRequestController(RejectJoinRequest rejectJoinRequest) {
        this.rejectJoinRequest = rejectJoinRequest;
    }

    @PostMapping("/reject")
    ResponseEntity<Void> reject(@PathVariable String joinRequestId) {
        rejectJoinRequest.reject(new JoinRequestId(uuid(joinRequestId)));
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
