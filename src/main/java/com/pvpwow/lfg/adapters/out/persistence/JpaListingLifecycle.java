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
 * <p>WHY SQL AND NOT JPA FOR THE CLOSE: the close is a set-based UPDATE
 * ("every ACTIVE listing of these two characters") and the facts are an
 * INSERT...SELECT of the same rows. In JPQL that is two round trips with a
 * re-read in between; in SQL it is one statement per table and the row set
 * is exactly the one the UPDATE touched.
 */
@Component
final class JpaListingLifecycle implements ListingLifecycle {

    private final ListingJpaRepository listings;
    private final JoinRequestJpaRepository joinRequests;
    private final ListingOutcomeJpaRepository outcomes;
    private final CharacterJpaRepository characterRepository;
    private final EntityManager entityManager;
    private final TransactionTemplate transactions;

    JpaListingLifecycle(ListingJpaRepository listings,
                         JoinRequestJpaRepository joinRequests,
                         ListingOutcomeJpaRepository outcomes,
                         CharacterJpaRepository characterRepository,
                         EntityManager entityManager,
                         PlatformTransactionManager transactionManager) {
        this.listings = listings;
        this.joinRequests = joinRequests;
        this.outcomes = outcomes;
        this.characterRepository = characterRepository;
        this.entityManager = entityManager;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public List<ListingOutcome> completeGroup(JoinRequest acceptedRequest,
                                               CharacterId ownerId,
                                               CharacterId requesterId,
                                               PlayerId requesterOwnerId,
                                               Instant now) {
        Objects.requireNonNull(acceptedRequest, "acceptedRequest must not be null");
        return transactions.execute(status -> {
            List<ListingOutcome> closed = new ArrayList<>();

            for (CharacterId characterId : List.of(ownerId, requesterId)) {
                closed.addAll(closeActiveListings(characterId, ListingOutcomeKind.COMPLETED, now));
            }

            // The requester is playing: every request still waiting for an
            // answer is cancelled, whichever character sent it.
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
            listing.setStatus(ListingStatus.CANCELLED);
            entityManager.merge(listing);
            ListingOutcome outcome = outcomeOf(listing, ListingOutcomeKind.CANCELLED, now);
            outcomes.save(ListingOutcomeMapper.toEntity(outcome));
            return outcome;
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
            listing.setStatus(ListingStatus.EXPIRED);
            entityManager.merge(listing);
            ListingOutcome outcome = outcomeOf(listing, ListingOutcomeKind.EXPIRED, now);
            outcomes.save(ListingOutcomeMapper.toEntity(outcome));
            return outcome;
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
    public int pendingRequestCount(PlayerId ownerId) {
        return joinRequests.countPending(ownerId.value(), JoinRequestStatus.PENDING);
    }

    /**
     * Closes every ACTIVE listing of a character and writes one fact per
     * closed listing. The listing set is read first and then closed one by
     * one, so the facts and the statuses can never disagree.
     *
     * <p>The entities are FLUSHED before the facts are written, because the
     * facts carry the listing's bracket and spec: they must be read from the
     * database, not from a stale persistence context.
     */
    private List<ListingOutcome> closeActiveListings(CharacterId characterId,
                                                     ListingOutcomeKind kind,
                                                     Instant now) {
        List<ListingEntity> active = listings.findByCharacterIdAndStatus(
                characterId.value(), ListingStatus.ACTIVE);
        for (ListingEntity listing : active) {
            listing.setStatus(ListingStatus.COMPLETED);
            entityManager.merge(listing);
        }
        entityManager.flush();

        List<ListingOutcome> closed = new ArrayList<>(active.size());
        for (ListingEntity listing : active) {
            ListingOutcome outcome = outcomeOf(listing, kind, now);
            outcomes.save(ListingOutcomeMapper.toEntity(outcome));
            closed.add(outcome);
        }
        return closed;
    }

    private ListingEntity requireListing(ListingId listingId) {
        return listings.findById(listingId.value())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Listing %s does not exist".formatted(listingId)));
    }

    private static ListingOutcome outcomeOf(ListingEntity listing, ListingOutcomeKind kind,
                                             Instant now) {
        return new ListingOutcome(
                new ListingId(listing.id()),
                new CharacterId(listing.character().id()),
                listing.bracket(),
                listing.spec(),
                kind,
                listing.createdAt(),
                now);
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
