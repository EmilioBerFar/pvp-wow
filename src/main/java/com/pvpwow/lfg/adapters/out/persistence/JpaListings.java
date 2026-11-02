package com.pvpwow.lfg.adapters.out.persistence;

import com.pvpwow.lfg.application.port.out.Listings;
import com.pvpwow.lfg.domain.character.Bracket;
import com.pvpwow.lfg.domain.character.CharacterId;
import com.pvpwow.lfg.domain.listing.LfgListing;
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
 */
@Component
final class JpaListings implements Listings {

    private final ListingJpaRepository repository;
    private final EntityManager entityManager;
    private final TransactionTemplate transactions;

    JpaListings(ListingJpaRepository repository,
                EntityManager entityManager,
                PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.entityManager = entityManager;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public void publish(LfgListing listing) {
        try {
            transactions.executeWithoutResult(status -> {
                repository.expireDue(listing.characterId().value(), listing.bracket(),
                        ListingStatus.ACTIVE, ListingStatus.EXPIRED, listing.createdAt());

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
