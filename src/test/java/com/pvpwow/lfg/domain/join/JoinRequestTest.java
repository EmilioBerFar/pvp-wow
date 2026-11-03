package com.pvpwow.lfg.domain.join;

import com.pvpwow.lfg.domain.character.CharacterId;
import com.pvpwow.lfg.domain.listing.ListingId;
import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JoinRequestTest {

    private static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");
    private static final Instant LATER = Instant.parse("2026-11-03T12:00:00Z");
    private static final CharacterId REQUESTER = new CharacterId(UUID.randomUUID());
    private static final ListingId LISTING = new ListingId(UUID.randomUUID());

    @Test
    void aNewRequestIsPendingAndLivesAsLongAsTheListing() {
        JoinRequest request = JoinRequest.request(REQUESTER, LISTING, NOW, LATER);

        assertThat(request.status()).isEqualTo(JoinRequestStatus.PENDING);
        assertThat(request.isPending()).isTrue();
        assertThat(request.createdAt()).isEqualTo(NOW);
        assertThat(request.expiresAt()).isEqualTo(LATER);
        assertThat(request.requesterId()).isEqualTo(REQUESTER);
        assertThat(request.listingId()).isEqualTo(LISTING);
    }

    @Test
    void aRequestThatDoesNotOutliveItsCreationIsRejected() {
        assertThatThrownBy(() -> JoinRequest.request(REQUESTER, LISTING, NOW, NOW))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("must expire after it was created");
    }

    @Test
    void acceptingIsAOneShotTransition() {
        JoinRequest request = JoinRequest.request(REQUESTER, LISTING, NOW, LATER);

        request.accept();

        assertThat(request.status()).isEqualTo(JoinRequestStatus.ACCEPTED);
        assertThat(request.isPending()).isFalse();
        assertThatThrownBy(request::accept)
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("Only a pending join request can be accepted");
    }

    @Test
    void rejectingIsAOneShotTransition() {
        JoinRequest request = JoinRequest.request(REQUESTER, LISTING, NOW, LATER);

        request.reject();

        assertThat(request.status()).isEqualTo(JoinRequestStatus.REJECTED);
        assertThatThrownBy(request::reject)
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    void cancellingIsAOneShotTransitionAndDiffersFromRejecting() {
        JoinRequest request = JoinRequest.request(REQUESTER, LISTING, NOW, LATER);

        request.cancel();

        assertThat(request.status()).isEqualTo(JoinRequestStatus.CANCELLED);
        assertThat(request.status())
                .as("the owner refused and the requester gave up are different facts")
                .isNotEqualTo(JoinRequestStatus.REJECTED);
    }

    @Test
    void anAnsweredRequestCannotBeWithdrawn() {
        JoinRequest request = JoinRequest.request(REQUESTER, LISTING, NOW, LATER);
        request.reject();

        assertThatThrownBy(request::cancel)
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("Only a pending join request can be cancelled");
    }

    @Test
    void aRequestExpiresExactlyWhenItsListingDoes() {
        JoinRequest request = JoinRequest.request(REQUESTER, LISTING, NOW, LATER);

        assertThat(request.expireIfDue(LATER.minusNanos(1)))
                .as("one nanosecond before the listing dies, the request is alive")
                .isFalse();
        assertThat(request.status()).isEqualTo(JoinRequestStatus.PENDING);

        assertThat(request.expireIfDue(LATER)).isTrue();
        assertThat(request.status()).isEqualTo(JoinRequestStatus.EXPIRED);
    }

    @Test
    void expiryIsIdempotent() {
        JoinRequest request = JoinRequest.request(REQUESTER, LISTING, NOW, LATER);

        assertThat(request.expireIfDue(LATER)).isTrue();
        assertThat(request.expireIfDue(LATER))
                .as("whatever runs the sweep may call it repeatedly")
                .isFalse();
        assertThat(request.status()).isEqualTo(JoinRequestStatus.EXPIRED);
    }

    @Test
    void anExpiredRequestCannotBeAccepted() {
        JoinRequest request = JoinRequest.request(REQUESTER, LISTING, NOW, LATER);
        request.expireIfDue(LATER);

        assertThatThrownBy(request::accept)
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    void twoRequestsWithTheSameIdAreTheSameRequest() {
        JoinRequestId id = new JoinRequestId(UUID.randomUUID());
        JoinRequest first = JoinRequest.rehydrate(id, REQUESTER, LISTING,
                JoinRequestStatus.PENDING, NOW, LATER);
        JoinRequest second = JoinRequest.rehydrate(id, new CharacterId(UUID.randomUUID()),
                new ListingId(UUID.randomUUID()), JoinRequestStatus.ACCEPTED, NOW, LATER);

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }
}
