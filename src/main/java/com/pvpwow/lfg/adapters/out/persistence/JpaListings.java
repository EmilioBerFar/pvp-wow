package com.pvpwow.lfg.adapters.out.persistence;

import com.pvpwow.lfg.application.port.out.Listings;
import com.pvpwow.lfg.domain.character.Bracket;
import com.pvpwow.lfg.domain.character.CharacterId;
import com.pvpwow.lfg.domain.listing.LfgListing;
import com.pvpwow.lfg.domain.listing.ListingOutcomeKind;
import com.pvpwow.lfg.domain.listing.ListingStatus;
import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.time.Instant;

/**
 * Listing writes against PostgreSQL. Publication is a single transaction:
 * expire the character's due ACTIVE row for this bracket, then insert the new
 * one. The partial unique index remains the final law when two publishers
 * race past the application-level pre-check.
 *
 * <p>WHY A PROGRAMMATIC TRANSACTION: the sweep and the insert must commit or
 * roll back together, and {@code @Transactional} cannot proxy this final
 * class. Why {@code persist} instead of {@code save}: the domain assigns the
 * UUID, so Spring Data's newness heuristic would route to {@code merge} and
 * skip the insert.
 *
 * <p>THE SWEEP CLOSES THROUGH THE SHARED CLOSER: the due listing that is
 * replaced here writes its EXPIRED fact and cancels its pending requests,
 * exactly like any other close. A close that skipped it would leave the
 * listing out of the metrics forever and leave the players who asked it
 * waiting for an answer that can never come.
 *
 * <p>The publish door accepts only a live aggregate. A CANCELLED or EXPIRED
 * listing reaching this adapter is a caller bug, not a business outcome, so it
 * fails before any SQL runs.
 */
@Component
final class JpaListings implements Listings {

    private final ListingJpaRepository repository;
    private final ListingCloser closer;
    private final EntityManager entityManager;
    private final TransactionTemplate transactions;

    JpaListings(ListingJpaRepository repository,
                ListingCloser closer,
                EntityManager entityManager,
                PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.closer = closer;
        this.entityManager = entityManager;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public void publish(LfgListing listing) {
        // A listing is published once, at the instant the aggregate was
        // created, so createdAt is the reference time for liveness. The
        // aggregate already guarantees expiresAt > createdAt; the guard is
        // here for the other half of the predicate: a CANCELLED or EXPIRED
        // aggregate must never resurrect as a live row.
        if (!listing.isActiveAt(listing.createdAt())) {
            throw new IllegalArgumentException(
                    "Only an active listing can be published, got: %s".formatted(listing));
        }
        try {
            transactions.executeWithoutResult(status -> {
                // The due row of this character and bracket is closed through
                // the shared closer: fact + pending requests, same as every
                // other close.
                closer.closeDue(listing.characterId(), listing.bracket(),
                        ListingStatus.EXPIRED, ListingOutcomeKind.EXPIRED, listing.createdAt());

                CharacterEntity character = entityManager.getReference(
                        CharacterEntity.class, listing.characterId().value());
                entityManager.persist(ListingEntityMapper.toEntity(listing, character));
                // Force the INSERT here, inside the try, so a unique-index
                // rejection is translated at this boundary instead of leaking
                // a PersistenceException to the caller.
                entityManager.flush();
            });
        } catch (RuntimeException failure) {
            if (isUniqueViolation(failure)
                    && hasActiveFor(listing.characterId(), listing.bracket(), listing.createdAt())) {
                throw new DomainRuleViolationException(
                        "Character %s already has an active %s listing"
                                .formatted(listing.characterId().value(), listing.bracket()));
            }
            throw failure;
        }
    }

    @Override
    public boolean hasActiveFor(CharacterId characterId, Bracket bracket, Instant now) {
        return repository.existsActive(characterId.value(), bracket, ListingStatus.ACTIVE, now);
    }

    /**
     * PostgreSQL signals a unique-index rejection with SQLState 23505. Reading
     * the SQLState keeps the adapter independent of the driver class, which is
     * runtime scope on purpose.
     */
    private static boolean isUniqueViolation(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
