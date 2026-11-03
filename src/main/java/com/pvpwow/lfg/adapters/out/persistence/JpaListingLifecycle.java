package com.pvpwow.lfg.adapters.out.persistence;

import com.pvpwow.lfg.application.port.out.ListingLifecycle;
import com.pvpwow.lfg.domain.character.CharacterId;
import com.pvpwow.lfg.domain.join.JoinRequest;
import com.pvpwow.lfg.domain.join.JoinRequestId;
import com.pvpwow.lfg.domain.join.JoinRequestStatus;
import com.pvpwow.lfg.domain.listing.LfgListing;
import com.pvpwow.lfg.domain.listing.ListingId;
import com.pvpwow.lfg.domain.listing.ListingOutcome;
import com.pvpwow.lfg.domain.listing.ListingOutcomeKind;
import com.pvpwow.lfg.domain.listing.ListingStatus;
import com.pvpwow.lfg.domain.player.PlayerId;
import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The lifecycle of a listing and of the join requests around it, in
 * PostgreSQL.
 *
 * <p>WHY A PROGRAMMATIC TRANSACTION: the atomic operations here touch
 * several tables at once — close listings, write their facts, cancel pending
 * requests — and they must commit or roll back together. A half-formed group
 * (announcements closed but no facts written) would poison the metrics, and
 * a group formed with no requests cancelled would leave the requester
 * waiting for an answer he no longer needs. {@code @Transactional} cannot
 * proxy this final class, so the transaction is explicit.
 *
 * <p>NO LISTING IS CLOSED HERE. Every close goes through {@link ListingCloser},
 * which always writes the fact and cancels the pending requests. This class
 * owns the transaction and the join-request bookkeeping around it.
 */
@Component
final class JpaListingLifecycle implements ListingLifecycle {

    private final ListingJpaRepository listings;
    private final JoinRequestJpaRepository joinRequests;
    private final CharacterJpaRepository characterRepository;
    private final ListingCloser closer;
    private final EntityManager entityManager;
    private final TransactionTemplate transactions;

    JpaListingLifecycle(ListingJpaRepository listings,
                         JoinRequestJpaRepository joinRequests,
                         CharacterJpaRepository characterRepository,
                         ListingCloser closer,
                         EntityManager entityManager,
                         PlatformTransactionManager transactionManager) {
        this.listings = listings;
        this.joinRequests = joinRequests;
        this.characterRepository = characterRepository;
        this.closer = closer;
        this.entityManager = entityManager;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public List<ListingOutcome> completeGroup(JoinRequest acceptedRequest,
                                               CharacterId ownerId,
                                               CharacterId requesterId,
                                               PlayerId ownerPlayerId,
                                               PlayerId requesterOwnerId,
                                               Instant now) {
        Objects.requireNonNull(acceptedRequest, "acceptedRequest must not be null");
        return transactions.execute(status -> {
            List<ListingOutcome> closed = new ArrayList<>();

            for (CharacterId characterId : List.of(ownerId, requesterId)) {
                closed.addAll(closer.closeAllOf(characterId, ListingStatus.COMPLETED,
                        ListingOutcomeKind.COMPLETED, now));
            }

            // BOTH players are playing now, so every request either of them
            // was waiting for is irrelevant: the owner's and the requester's,
            // whichever character sent them.
            joinRequests.cancelPending(ownerPlayerId.value(),
                    JoinRequestStatus.PENDING, JoinRequestStatus.CANCELLED);
            joinRequests.cancelPending(requesterOwnerId.value(),
                    JoinRequestStatus.PENDING, JoinRequestStatus.CANCELLED);

            // The accepted request is marked ACCEPTED by the ADAPTER, not by
            // the aggregate it was given: the aggregate is a value, and the
            // row is what the next reader sees.
            joinRequests.updateStatus(acceptedRequest.id().value(),
                    JoinRequestStatus.ACCEPTED);
            entityManager.flush();
            return List.copyOf(closed);
        });
    }

    @Override
    public ListingOutcome withdraw(ListingId listingId, CharacterId characterId, Instant now) {
        return transactions.execute(status -> {
            ListingEntity listing = requireListing(listingId);
            if (listing.status() != ListingStatus.ACTIVE) {
                throw new DomainRuleViolationException(
                        "Only an ACTIVE listing can be withdrawn, current status: %s"
                                .formatted(listing.status()));
            }
            // The shared closer: fact + pending requests, in one place.
            return closer.close(listing, ListingStatus.CANCELLED,
                    ListingOutcomeKind.CANCELLED, now);
        });
    }

    @Override
    public ListingOutcome expire(ListingId listingId, CharacterId characterId, Instant now) {
        return transactions.execute(status -> {
            ListingEntity listing = requireListing(listingId);
            if (listing.status() != ListingStatus.ACTIVE) {
                throw new DomainRuleViolationException(
                        "Only an ACTIVE listing can expire, current status: %s"
                                .formatted(listing.status()));
            }
            // The shared closer: fact + pending requests, in one place.
            return closer.close(listing, ListingStatus.EXPIRED,
                    ListingOutcomeKind.EXPIRED, now);
        });
    }

    @Override
    public Optional<LfgListing> findListing(ListingId listingId) {
        return listings.findById(listingId.value())
                .map(ListingEntityMapper::toDomain);
    }

    @Override
    public Optional<JoinRequest> findJoinRequest(JoinRequestId joinRequestId) {
        return joinRequests.findById(joinRequestId.value())
                .map(JoinRequestMapper::toDomain);
    }

    @Override
    public void save(JoinRequest request) {
        transactions.executeWithoutResult(status -> {
            if (joinRequests.existsById(request.id().value())) {
                joinRequests.updateStatus(request.id().value(), request.status());
                entityManager.flush();
            } else {
                persistJoinRequest(request);
            }
        });
    }

    @Override
    public boolean hasPendingRequest(PlayerId ownerId, CharacterId requesterId,
                                      UUID listingId) {
        return joinRequests.existsPending(ownerId.value(), listingId,
                JoinRequestStatus.PENDING);
    }

    @Override
    public int pendingRequestCount(PlayerId ownerId, Instant now) {
        return joinRequests.countPending(ownerId.value(), JoinRequestStatus.PENDING, now);
    }

    private ListingEntity requireListing(ListingId listingId) {
        return listings.findById(listingId.value())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Listing %s does not exist".formatted(listingId)));
    }

    private void persistJoinRequest(JoinRequest request) {
        JoinRequestEntity entity = JoinRequestMapper.toEntity(request, ownerIdOf(request));
        joinRequests.save(entity);
        entityManager.flush();
    }

    /**
     * The player who sent the request. The request knows its character, and
     * the character knows its owner.
     *
     * <p>The character is READ, not referenced: a proxy would carry the id
     * but not the owner id, and the owner id is exactly what the row needs.
     */
    private UUID ownerIdOf(JoinRequest request) {
        return characterRepository.findById(request.requesterId().value())
                .map(CharacterEntity::ownerId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Character %s does not exist".formatted(request.requesterId())));
    }
}
