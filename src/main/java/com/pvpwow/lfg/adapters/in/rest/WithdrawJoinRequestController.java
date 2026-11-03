package com.pvpwow.lfg.adapters.in.rest;

import com.pvpwow.lfg.application.port.in.WithdrawJoinRequest;
import com.pvpwow.lfg.domain.join.JoinRequestId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The requester takes their request back. Not the same as a rejection: the
 * owner never answered, and the metrics must be able to tell those apart.
 */
@RestController
@RequestMapping("/api/join-requests/{joinRequestId}")
public final class WithdrawJoinRequestController {

    private final WithdrawJoinRequest withdrawJoinRequest;

    public WithdrawJoinRequestController(WithdrawJoinRequest withdrawJoinRequest) {
        this.withdrawJoinRequest = withdrawJoinRequest;
    }

    @PostMapping("/withdraw")
    ResponseEntity<Void> withdraw(@PathVariable String joinRequestId) {
        withdrawJoinRequest.withdraw(new JoinRequestId(uuid(joinRequestId)));
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
